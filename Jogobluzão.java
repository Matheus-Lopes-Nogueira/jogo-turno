import javax.swing.*;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Random;

public class Jogobluzão {

    // ===== CONFIG GERAL =====
    static final int PLAYER_PV_MAX_BASE = 30;  // base antes do multiplicador por nível
    static final int PLAYER_PA_INICIO = 3;
    static final int PLAYER_PA_MAX = 10;

    // Ataque básico (valores base; dano é multiplicado pelo nível)
    static final int BASIC_DMG_MIN = 6;
    static final int BASIC_DMG_MAX = 10;
    static final double BASIC_HIT_CHANCE = 0.90;     // 90%
    static final double BASIC_CRIT_CHANCE = 0.05;    // 5%
    static final double BASIC_CRIT_MULT = 2.5;       // x2.5
    static final int BASIC_PA_GANHO = 1;

    // Habilidade (valores base; dano é multiplicado pelo nível)
    static final int SKILL_DMG_MIN = 18;
    static final int SKILL_DMG_MAX = 24;

    // ===== FUGA =====
    static final double FLEE_BASE_CHANCE = 0.50; // 50% base
    static final double FLEE_MIN = 0.10;         // nunca menor que 10%
    static final double FLEE_MAX = 0.95;         // nunca maior que 95%

    // ===== MODELOS =====
    static class Combatente {
        String nome;
        int pv, pvMax, pa, paMax;

        Combatente(String nome, int pvMax, int paInicio, int paMax) {
            this.nome = nome;
            this.pvMax = pvMax;
            this.pv = pvMax;
            this.pa = paInicio;
            this.paMax = paMax;
        }
        boolean vivo() { return pv > 0; }
    }

    static class Inimigo extends Combatente {
        int danoMin, danoMax;
        double chanceAcerto;

        Inimigo(String nome, int pvMax, int danoMin, int danoMax, double chanceAcerto) {
            super(nome, pvMax, 0, 0);
            this.danoMin = danoMin;
            this.danoMax = danoMax;
            this.chanceAcerto = chanceAcerto;
        }
    }

    static class Habilidade {
        String nome;
        String desc;
        int custoPA;
        int danoMin, danoMax;

        Habilidade(String nome, String desc, int custoPA, int danoMin, int danoMax) {
            this.nome = nome;
            this.desc = desc;
            this.custoPA = custoPA;
            this.danoMin = danoMin;
            this.danoMax = danoMax;
        }
    }

    static class Item {
        String nome;
        String desc;
        int cura;
        int qtd;

        Item(String nome, String desc, int cura, int qtd) {
            this.nome = nome;
            this.desc = desc;
            this.cura = cura;
            this.qtd = qtd;
        }
    }

    // ===== CONTEÚDO EXPANSÍVEL =====
    static final Habilidade[] HABILIDADES = new Habilidade[] {
        new Habilidade("Golpe Amaldiçoado", "Golpe pesado que consome energia sombria.", 5, SKILL_DMG_MIN, SKILL_DMG_MAX)
    };

    static final Inimigo[] BESTIARIO = new Inimigo[] {
        new Inimigo("Rato de Esgoto", 35, 4, 8, 0.80)
        // Futuro: new Inimigo("Slime Verde", 28, 3, 7, 0.85)
    };

    // Inventário inicial
    static final Item[] INVENTARIO = new Item[] {
        new Item("Poção Pequena", "Cura moderada.", 25, 2)
        // Futuro: new Item("Éter", "Recupera PA.", 0, 1)
    };

    // ===== NIVELAMENTO =====
    static int PLAYER_NIVEL = 1;
    static double PLAYER_HP_MULT = 1.0;   // +20% por nível acima de 1
    static double PLAYER_DMG_MULT = 1.0;  // +15% por nível acima de 1

    // ===== MÚSICA =====
    // 1) Caminho no classpath (ideal pra rodar do IDE ou .jar)
    private static final String MUSIC_RESOURCE_PATH =
            "/musica/Battle_-_Trainer-Battle_-Pokémon-FireRed-_-Pokémon-LeafGreen-_OST_.wav";
    // 2) Caminho relativo (pasta do projeto: jogo-turno/musica/...)
    private static final String DEFAULT_MUSIC_PATH =
            "musica/Battle_-_Trainer-Battle_-Pokémon-FireRed-_-Pokémon-LeafGreen-_OST_.wav";

    static class MusicPlayer {
        private Clip clip;
        private boolean paused = false;
        private long pausePosMicros = 0;

        void playLoopFromFile(String path) throws Exception {
            stop();
            File f = new File(path);
            if (!f.exists()) throw new IOException("Arquivo não encontrado: " + path);
            try (AudioInputStream ais = AudioSystem.getAudioInputStream(f)) {
                startClip(ais);
            }
        }
        void playLoopFromURL(String urlStr) throws Exception {
            stop();
            URL url = new URL(urlStr);
            try (AudioInputStream ais = AudioSystem.getAudioInputStream(url)) {
                startClip(ais);
            }
        }
        private void startClip(AudioInputStream ais) throws Exception {
            AudioFormat base = ais.getFormat();
            AudioFormat dec = base;
            if (base.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
                dec = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                        base.getSampleRate(), 16, base.getChannels(),
                        base.getChannels() * 2, base.getSampleRate(), false);
                ais = AudioSystem.getAudioInputStream(dec, ais);
            }
            DataLine.Info info = new DataLine.Info(Clip.class, dec);
            clip = (Clip) AudioSystem.getLine(info);
            clip.open(ais);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            clip.start();
            paused = false;
            pausePosMicros = 0;
        }
        void togglePause() {
            if (clip == null) return;
            if (!paused) {
                pausePosMicros = clip.getMicrosecondPosition();
                clip.stop();
                paused = true;
            } else {
                clip.setMicrosecondPosition(pausePosMicros);
                clip.start();
                paused = false;
            }
        }
        void stop() {
            if (clip != null) {
                clip.stop();
                clip.flush();
                clip.close();
                clip = null;
            }
            paused = false;
            pausePosMicros = 0;
        }
    }
    static final MusicPlayer BGM = new MusicPlayer();

    // ===== JANELA PRINCIPAL (HUD + Ações + Log) =====
    static class GameFrame extends JFrame {
        final JLabel hud = new JLabel("<html>...</html>");
        final JTextArea log = new JTextArea(12, 28);
        final JButton btnAtk = new JButton("Ataque Básico");
        final JButton btnHabilidade = new JButton("Habilidade ▾");
        final JButton btnItem = new JButton("Item ▾");
        final JButton btnFugir = new JButton("Fugir");
        final JButton btnMusica = new JButton("Música ▶/⏸");

        final JPopupMenu menuHabs = new JPopupMenu();
        final JPopupMenu menuItens = new JPopupMenu();

        Combatente jogador;
        Inimigo inimigo;
        final Random rng = new Random();

        GameFrame(Combatente jogador, Inimigo inimigo) {
            super("Jogobluzão — Batalha");
            this.jogador = jogador;
            this.inimigo = inimigo;

            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            setLayout(new BorderLayout(10,10));

            // HUD
            hud.setVerticalAlignment(SwingConstants.TOP);
            JScrollPane hudScroll = new JScrollPane(hud);
            hudScroll.setPreferredSize(new Dimension(520, 320));

            // Log fixo (não zera)
            log.setEditable(false);
            log.setLineWrap(true);
            log.setWrapStyleWord(true);
            log.setFont(new Font("Consolas", Font.PLAIN, 13));
            JScrollPane logScroll = new JScrollPane(log);
            logScroll.setPreferredSize(new Dimension(360, 320));

            // Botões
            JPanel actions = new JPanel(new GridLayout(1, 5, 8, 8));
            for (JButton b : new JButton[]{btnAtk, btnHabilidade, btnItem, btnFugir, btnMusica}) {
                b.setFocusPainted(false);
                actions.add(b);
            }

            add(hudScroll, BorderLayout.CENTER);
            add(logScroll, BorderLayout.EAST);
            add(actions, BorderLayout.SOUTH);

            // Ações
            btnAtk.addActionListener(e -> onAtkBasico());
            btnHabilidade.addActionListener(this::showHabilidadeMenu);
            btnItem.addActionListener(this::showItemMenu);
            btnFugir.addActionListener(e -> onFugir());
            btnMusica.addActionListener(e -> BGM.togglePause());

            montarMenus(); // cria menus pop-up

            pack();
            setLocationRelativeTo(null);
            setVisible(true);

            updateHUD();
            addLog("Um " + inimigo.nome + " apareceu!");
            addLog("Seu nível: " + PLAYER_NIVEL);
        }

        // ===== Menus embutidos =====
        void montarMenus() {
            // Habilidades
            menuHabs.removeAll();
            for (int i = 0; i < HABILIDADES.length; i++) {
                final int idx = i;
                Habilidade h = HABILIDADES[i];
                String label = h.nome + " — Custo: " + h.custoPA + " PA  | Dano: " + h.danoMin + "-" + h.danoMax;
                JMenuItem it = new JMenuItem(label);
                it.addActionListener(e -> executarHabilidade(idx));
                menuHabs.add(it);
            }
            menuHabs.addSeparator();
            JMenuItem voltarHab = new JMenuItem("Voltar");
            voltarHab.addActionListener(e -> menuHabs.setVisible(false));
            menuHabs.add(voltarHab);

            // Itens
            rebuildItensMenu();
        }

        void rebuildItensMenu() {
            menuItens.removeAll();
            boolean tem = false;
            for (int i = 0; i < INVENTARIO.length; i++) {
                Item it = INVENTARIO[i];
                if (it.qtd > 0) {
                    tem = true;
                    final int idx = i;
                    JMenuItem mi = new JMenuItem(it.nome + " (x" + it.qtd + ") — " + it.desc);
                    mi.addActionListener(e -> usarItem(idx));
                    menuItens.add(mi);
                }
            }
            if (!tem) {
                JMenuItem vazio = new JMenuItem("Sem itens disponíveis");
                vazio.setEnabled(false);
                menuItens.add(vazio);
            }
            menuItens.addSeparator();
            JMenuItem voltar = new JMenuItem("Voltar");
            voltar.addActionListener(e -> menuItens.setVisible(false));
            menuItens.add(voltar);
        }

        void showHabilidadeMenu(ActionEvent e) {
            menuHabs.show(btnHabilidade, 0, btnHabilidade.getHeight());
        }
        void showItemMenu(ActionEvent e) {
            rebuildItensMenu();
            menuItens.show(btnItem, 0, btnItem.getHeight());
        }

        // ===== Log/ HUD =====
        void addLog(String s) {
            log.append(s + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        }
        void updateHUD() {
            hud.setText(montarHUD(jogador, inimigo, inventarioResumo()));
        }
        void lockButtons(boolean lock) {
            btnAtk.setEnabled(!lock);
            btnHabilidade.setEnabled(!lock);
            btnItem.setEnabled(!lock);
            btnFugir.setEnabled(!lock);
            btnMusica.setEnabled(!lock);
        }

        // ====== Ações ======
        void onAtkBasico() {
            if (!jogador.vivo() || !inimigo.vivo()) return;

            // Ganha PA ao declarar o ataque (independe de acertar)
            int novoPA = Math.min(jogador.paMax, jogador.pa + BASIC_PA_GANHO);
            animatePAChangeAsync(jogador, novoPA);

            // Resolve o ataque (pode errar)
            int dano = calcularDanoBasico(rng);
            if (dano <= 0) {
                addLog("Você atacou, mas ERROU! (+" + BASIC_PA_GANHO + " PA)");
                enemyTurnAfterDelay();
                return;
            }
            boolean crit = foiCritico(dano, inimigo.pv);
            int novoPV = Math.max(0, inimigo.pv - dano);
            animatePVChangeAsync(inimigo, novoPV);
            addLog((crit ? "Crítico! " : "") + "Você causou " + dano + " de dano! (+" + BASIC_PA_GANHO + " PA)");

            checkEndOrEnemyTurn();
        }

        void executarHabilidade(int idxHab) {
            if (!jogador.vivo() || !inimigo.vivo()) return;
            Habilidade h = HABILIDADES[idxHab];
            if (jogador.pa < h.custoPA) {
                addLog("PA insuficiente para " + h.nome + " (precisa " + h.custoPA + ").");
                return;
            }

            // PA primeiro (anima descendo)
            int novoPA = Math.max(0, jogador.pa - h.custoPA);
            animatePAChangeAsync(jogador, novoPA);

            int dano = calcularDanoHabilidade(rng, h);
            int novoPV = Math.max(0, inimigo.pv - dano);
            animatePVChangeAsync(inimigo, novoPV);
            addLog("Você usou " + h.nome + " e causou " + dano + " de dano!");

            checkEndOrEnemyTurn();
        }

        void usarItem(int idxItem) {
            Item escolhido = INVENTARIO[idxItem];
            if (escolhido.qtd <= 0) { addLog("Sem " + escolhido.nome + "."); return; }

            int antes = jogador.pv;
            int depois = Math.min(jogador.pvMax, jogador.pv + escolhido.cura);
            int curado = depois - antes;
            if (curado <= 0) { addLog("PV já está cheio."); return; }

            escolhido.qtd--;
            animatePVChangeAsync(jogador, depois);
            addLog("Você usou " + escolhido.nome + " e curou " + curado + " PV.");
            rebuildItensMenu(); // atualiza contagem no menu

            enemyTurnAfterDelay();
        }

        void onFugir() {
            if (!jogador.vivo() || !inimigo.vivo()) return;

            double chance = computeFleeChance(jogador, inimigo);
            int pct = (int)Math.round(chance * 100);

            if (rng.nextDouble() < chance) {
                addLog("Você tentou fugir... SUCESSO (" + pct + "%).");
                lockButtons(true);
                dispose(); // encerra a batalha
            } else {
                addLog("Você tentou fugir... FALHOU (" + pct + "%)!");
                enemyTurnAfterDelay(); // falhou → inimigo age
            }
        }

        void checkEndOrEnemyTurn() {
            if (!inimigo.vivo()) {
                updateHUD();
                addLog("O " + inimigo.nome + " foi derrotado! Vitória!");
                lockButtons(true);
                return;
            }
            enemyTurnAfterDelay();
        }

        void enemyTurnAfterDelay() {
            lockButtons(true);
            new Timer(600, e -> {
                ((Timer)e.getSource()).stop();
                enemyTurn();
            }).start();
        }

        void enemyTurn() {
            if (!jogador.vivo() || !inimigo.vivo()) { lockButtons(true); return; }
            if (rng.nextDouble() <= inimigo.chanceAcerto) {
                int dano = rand(rng, inimigo.danoMin, inimigo.danoMax);
                int novo = Math.max(0, jogador.pv - dano);
                animatePVChangeAsync(jogador, novo);
                addLog(inimigo.nome + " acertou e causou " + dano + " de dano!");
            } else {
                addLog(inimigo.nome + " errou o ataque!");
            }
            if (!jogador.vivo()) {
                updateHUD();
                addLog("Você caiu... Derrota!");
                lockButtons(true);
            } else {
                lockButtons(false);
            }
        }

        // ====== Animações assíncronas ======
        void animatePVChangeAsync(Combatente alvo, int novoPV) {
            final int targetPV = Math.max(0, Math.min(novoPV, alvo.pvMax));
            final int from = alvo.pv;
            if (from == targetPV) { updateHUD(); return; }

            lockButtons(true);
            new Thread(() -> {
                int step = (targetPV > from) ? 1 : -1;
                int cur = from;
                while (cur != targetPV) {
                    cur += step;
                    alvo.pv = cur;
                    SwingUtilities.invokeLater(this::updateHUD);
                    try { Thread.sleep(16); } catch (InterruptedException ignored) {}
                }
                SwingUtilities.invokeLater(this::updateHUD);
                SwingUtilities.invokeLater(() -> lockButtons(false));
            }, "animPV").start();
        }

        void animatePAChangeAsync(Combatente alvo, int novoPA) {
            final int targetPA = Math.max(0, Math.min(novoPA, alvo.paMax));
            final int from = alvo.pa;
            if (from == targetPA) { updateHUD(); return; }

            lockButtons(true);
            new Thread(() -> {
                int step = (targetPA > from) ? 1 : -1;
                int cur = from;
                while (cur != targetPA) {
                    cur += step;
                    alvo.pa = cur;
                    SwingUtilities.invokeLater(this::updateHUD);
                    try { Thread.sleep(16); } catch (InterruptedException ignored) {}
                }
                SwingUtilities.invokeLater(this::updateHUD);
                SwingUtilities.invokeLater(() -> lockButtons(false));
            }, "animPA").start();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                iniciarMusicaComFallback();
                Inimigo inimigo = escolherInimigo(null);
                if (inimigo == null) { BGM.stop(); return; }
                PLAYER_NIVEL = escolherNivel(null);
                if (PLAYER_NIVEL == -1) { BGM.stop(); return; }
                aplicarMultiplicadoresPorNivel(PLAYER_NIVEL);

                int pvMaxAjustado = (int)Math.round(PLAYER_PV_MAX_BASE * PLAYER_HP_MULT);
                Combatente jogador = new Combatente("Você", pvMaxAjustado, PLAYER_PA_INICIO, PLAYER_PA_MAX);

                new GameFrame(jogador, inimigo);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // ===== Habilidade / Item helpers =====
    static int calcularDanoBasico(Random rng) {
        if (rng.nextDouble() > BASIC_HIT_CHANCE) return 0; // errou
        int danoBase = rand(rng, BASIC_DMG_MIN, BASIC_DMG_MAX);
        int dano = (int)Math.round(danoBase * PLAYER_DMG_MULT);
        boolean crit = rng.nextDouble() < BASIC_CRIT_CHANCE;
        if (crit) dano = (int)Math.round(dano * BASIC_CRIT_MULT);
        return dano;
    }
    static boolean foiCritico(int danoAplicado, int pvAntesAlvo) {
        int baseMin = (int)Math.round(BASIC_DMG_MIN * PLAYER_DMG_MULT);
        return danoAplicado >= Math.round(baseMin * BASIC_CRIT_MULT) && danoAplicado > 0 && danoAplicado <= pvAntesAlvo;
    }
    static int calcularDanoHabilidade(Random rng, Habilidade h) {
        int danoBase = rand(rng, h.danoMin, h.danoMax);
        return (int)Math.round(danoBase * PLAYER_DMG_MULT);
    }

    // ===== Fuga helpers =====
    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
    /** Chance de fuga considerando nível, seu PV relativo e PV do inimigo. */
    static double computeFleeChance(Combatente jogador, Inimigo inimigo) {
        double pBase = FLEE_BASE_CHANCE; // 50% base
        double pLvl  = 0.05 * (PLAYER_NIVEL - 1); // +5% por nível acima de 1
        double pYou  = 0.10 * ((double)jogador.pv / Math.max(1, jogador.pvMax)); // até +10% se cheio
        double pEn   = 0.20 * (1.0 - (double)inimigo.pv / Math.max(1, inimigo.pvMax)); // até +20% se inimigo baixo
        double p = pBase + pLvl + pYou + pEn;
        return clamp(p, FLEE_MIN, FLEE_MAX);
    }

    // ===== Música / Escolhas pré-batalha =====
    static void iniciarMusicaComFallback() {
        // 1) tenta do classpath: /musica/arquivo.wav
        URL res = Jogobluzão.class.getResource(MUSIC_RESOURCE_PATH);
        if (res != null) {
            try {
                BGM.playLoopFromURL(res.toString());
                return;
            } catch (Exception ignore) { /* cai para próxima opção */ }
        }
        // 2) tenta caminho relativo: musica/arquivo.wav
        try {
            File f = new File(DEFAULT_MUSIC_PATH);
            if (f.exists()) {
                BGM.playLoopFromFile(DEFAULT_MUSIC_PATH);
                return;
            }
        } catch (Exception ignore) { /* cai para fallback interativo */ }

        // 3) fallback interativo
        try {
            String[] opts = {"Escolher arquivo local...", "Usar link (WAV)", "Sem música"};
            int sel = showOptions(null, "Música de Fundo",
                    "Não foi possível carregar a música automaticamente.\n" +
                    "Tente selecionar manualmente:",
                    opts, 0);
            if (sel == 0) {
                JFileChooser fc = new JFileChooser();
                fc.setDialogTitle("Escolha um arquivo de áudio (WAV/AIFF/AU)");
                int r = fc.showOpenDialog(null);
                if (r == JFileChooser.APPROVE_OPTION) {
                    File f = fc.getSelectedFile();
                    BGM.playLoopFromFile(f.getAbsolutePath());
                }
            } else if (sel == 1) {
                String url = JOptionPane.showInputDialog(null,
                        "Cole a URL de um arquivo WAV/AIFF/AU (http/https):",
                        "https://exemplo.com/som.wav");
                if (url != null && !url.isBlank()) BGM.playLoopFromURL(url.trim());
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, "Falha ao carregar áudio: " + ex.getMessage(),
                    "Erro de Áudio", JOptionPane.ERROR_MESSAGE);
        }
    }

    static Inimigo escolherInimigo(Component parent) {
        String[] nomes = new String[BESTIARIO.length];
        for (int i = 0; i < BESTIARIO.length; i++) nomes[i] = BESTIARIO[i].nome;

        int escolha = showOptions(parent, "Selecionar Inimigo", "Escolha o inimigo para a batalha (mais serão adicionados no futuro):", nomes, 0);
        if (escolha == -1) return null;
        Inimigo base = BESTIARIO[escolha];
        return new Inimigo(base.nome, base.pvMax, base.danoMin, base.danoMax, base.chanceAcerto);
    }

    static int escolherNivel(Component parent) {
        String[] niveis = {"1", "2", "3", "4", "5"};
        int sel = showOptions(parent, "Selecionar Nível", "Escolha o nível do seu personagem (1 a 5):", niveis, 0);
        if (sel == -1) return -1;
        return sel + 1;
    }

    // ===== UTIL =====
    static void aplicarMultiplicadoresPorNivel(int nivel) {
        PLAYER_HP_MULT = 1.0 + 0.20 * (nivel - 1);
        PLAYER_DMG_MULT = 1.0 + 0.15 * (nivel - 1);
    }
    static int rand(Random rng, int min, int max) { return rng.nextInt((max - min) + 1) + min; }

    // Resumo simples do inventário para a HUD
    static String inventarioResumo() {
        StringBuilder sb = new StringBuilder();
        boolean primeiro = true;
        for (Item it : INVENTARIO) {
            if (it.qtd > 0) {
                if (!primeiro) sb.append(" | ");
                sb.append(it.nome).append(" x").append(it.qtd);
                primeiro = false;
            }
        }
        return primeiro ? "—" : sb.toString();
    }

    // --------- HUD EM HTML ----------
    static String montarHUD(Combatente jogador, Inimigo inimigo, String invResumo) {
        String playerHP = barHP(jogador.pv, jogador.pvMax);
        String playerPA = barPA(jogador.pa, jogador.paMax);
        String enemyHP  = barEnemy(jogador, inimigo);

        String acerto = (int)(BASIC_HIT_CHANCE * 100) + "%";
        String crit = (int)(BASIC_CRIT_CHANCE * 100) + "%";
        String fuga = (int)Math.round(computeFleeChance(jogador, inimigo) * 100) + "%";

        return "<html>" +
            "<div style='font-family:Segoe UI, sans-serif; width:520px'>" +
            "<h3 style='margin:0 0 6px 0'>⚔️ BATALHA</h3>" +
            "<table style='width:100%; border-collapse:collapse; font-size:12px'>" +
            " <tr>" +
            "   <td style='vertical-align:top; width:50%; padding:6px; border:1px solid #ddd'>" +
            "     <div><b>" + esc(jogador.nome) + "</b> — Nível " + PLAYER_NIVEL + "</div>" +
            "     <div style='margin-top:6px'>❤️ PV: " + jogador.pv + "/" + jogador.pvMax + "</div>" +
            "     " + playerHP +
            "     <div style='margin-top:6px'>🔮 PA: " + jogador.pa + "/" + jogador.paMax + "</div>" +
            "     " + playerPA +
            "   </td>" +
            "   <td style='vertical-align:top; width:50%; padding:6px; border:1px solid #ddd'>" +
            "     <div><b>" + esc(inimigo.nome) + "</b></div>" +
            "     <div style='margin-top:6px'>❤️ PV: " + inimigo.pv + "/" + inimigo.pvMax + "</div>" +
            "     " + enemyHP +
            "     <div style='margin-top:8px; color:#555'>Acerto inimigo: " + (int)(inimigo.chanceAcerto*100) + "%</div>" +
            "   </td>" +
            " </tr>" +
            "</table>" +
            "<div style='margin-top:8px; font-size:12px; color:#555'>" +
            "  <b>Dicas:</b> Ataque Básico = +" + BASIC_PA_GANHO + " PA | Chance de acerto: " + acerto +
            " | Crítico: " + crit + " | Fuga: " + fuga +
            "</div>" +
            "<div style='margin-top:8px; font-size:12px; color:#555'>Inventário: " + esc(invResumo) + "</div>" +
            "</div>" +
            "</html>";
    }

    // --- Barras modernas (proporcionais e com cor dinâmica) ---
    static String barHP(int cur, int max) {
        cur = Math.max(0, Math.min(cur, max));
        double ratio = (max == 0) ? 0 : (double) cur / max;

        Color fill;
        if (ratio > 0.66) fill = new Color(34, 197, 94);      // #22c55e
        else if (ratio > 0.33) fill = new Color(234, 179, 8); // #eab308
        else fill = new Color(239, 68, 68);                   // #ef4444

        return filledBar(ratio, colorHex(fill), "#111827");
    }

    static String barPA(int cur, int max) {
        cur = Math.max(0, Math.min(cur, max));
        double ratio = (max == 0) ? 0 : (double) cur / max;

        Color start = new Color(59,130,246); // #3b82f6
        Color end   = new Color(124,58,237); // #7c3aed
        Color fill  = lerp(start, end, ratio);

        return filledBar(ratio, colorHex(fill), "#0b1220");
    }

    static String barEnemy(Combatente jogador, Inimigo inimigo) {
        int cur = Math.max(0, Math.min(inimigo.pv, inimigo.pvMax));
        double ratio = (inimigo.pvMax == 0) ? 0 : (double) cur / inimigo.pvMax;

        Color high = new Color(239,68,68);   // #ef4444
        Color low  = new Color(127,29,29);   // #7f1d1d
        Color fill = lerp(low, high, ratio);

        return filledBar(ratio, colorHex(fill), "#1f0a0a");
    }

    static String filledBar(double ratio, String fillHex, String backHex) {
        int pct = (int)Math.round(100 * Math.max(0, Math.min(1, ratio)));
        return "<div style='display:inline-block;width:220px;height:14px;"
             + "background:" + backHex + ";border-radius:6px;overflow:hidden;'>"
             + "<div style='width:" + pct + "%;height:100%;background:" + fillHex + ";'></div>"
             + "</div>";
    }

    static Color lerp(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int)Math.round(a.getRed()   + (b.getRed()   - a.getRed())   * t);
        int g = (int)Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl= (int)Math.round(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t);
        return new Color(r,g,bl);
    }
    static String colorHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static int showOptions(Component parent, String titulo, String mensagem, String[] opcoes, int defaultIndex) {
        return JOptionPane.showOptionDialog(
                parent,
                mensagem,
                titulo,
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                opcoes,
                opcoes[Math.max(0, Math.min(defaultIndex, opcoes.length - 1))]
        );
    }
}
