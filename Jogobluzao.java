import javax.swing.*;
import java.awt.Color;
import java.io.File;
import java.net.URL;
import java.util.Random;

public class Jogobluzao {

    // ===== CONFIG GERAL =====
    public static final int PLAYER_PV_MAX_BASE = 30;  // base antes do multiplicador por nível
    public static final int PLAYER_PA_INICIO = 3;
    public static final int PLAYER_PA_MAX = 10;

    // Ataque básico (valores base; dano é multiplicado pelo nível)
    public static final int BASIC_DMG_MIN = 6;
    public static final int BASIC_DMG_MAX = 10;
    public static final double BASIC_HIT_CHANCE = 0.90;     // 90%
    public static final double BASIC_CRIT_CHANCE = 0.05;    // 5%
    public static final double BASIC_CRIT_MULT = 2.5;       // x2.5
    public static final int BASIC_PA_GANHO = 1;

    // Habilidade (valores base; dano é multiplicado pelo nível)
    public static final int SKILL_DMG_MIN = 18;
    public static final int SKILL_DMG_MAX = 24;

    // ===== FUGA =====
    public static final double FLEE_BASE_CHANCE = 0.50; // 50% base
    public static final double FLEE_MIN = 0.10;         // nunca menor que 10%
    public static final double FLEE_MAX = 0.95;         // nunca maior que 95%

    // ===== CONTEÚDO EXPANSÍVEL =====
    public static final Habilidade[] HABILIDADES = new Habilidade[] {
        new Habilidade(
            "Golpe Amaldiçoado",
            "Golpe pesado que consome energia sombria.",
            5,
            SKILL_DMG_MIN,
            SKILL_DMG_MAX
        ),
        new Habilidade(
            "Danonão Grosso",
            "Um danonão nervoso que causa dano moderado e deixa o inimigo meio zonzo, reduzindo a chance de acerto.",
            4,   // custo em PA
            8,   // dano mínimo
            12   // dano máximo
        )
    };

    public static final Inimigo[] BESTIARIO = new Inimigo[] {
        new Inimigo("Rato de Esgoto", 35, 4, 8, 0.80)
        // Futuro: new Inimigo("Slime Verde", 28, 3, 7, 0.85)
    };

    // Inventário inicial
    public static final Item[] INVENTARIO = new Item[] {
        new Item("Poção Pequena", "Cura moderada.", 25, 2)
        // Futuro: new Item("Éter", "Recupera PA.", 0, 1)
    };

    // ===== NIVELAMENTO =====
    public static int PLAYER_NIVEL = 1;
    public static double PLAYER_HP_MULT = 1.0;   // +20% por nível acima de 1
    public static double PLAYER_DMG_MULT = 1.0;  // +15% por nível acima de 1

    // ===== MÚSICA =====
    // 1) Caminho no classpath (ideal pra rodar do IDE ou .jar)
    public static final String MUSIC_RESOURCE_PATH =
            "/musica/Battle_-_Trainer-Battle_-Pokémon-FireRed-_-Pokémon-LeafGreen-_OST_.wav";
    // 2) Caminho relativo (pasta do projeto: jogo-turno/musica/...)
    public static final String DEFAULT_MUSIC_PATH =
            "musica/Battle_-_Trainer-Battle_-Pokémon-FireRed-_-Pokémon-LeafGreen-_OST_.wav";

    public static final MusicPlayer BGM = new MusicPlayer();

    // ================= MAIN =================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                iniciarMusicaComFallback();

                Inimigo inimigo = escolherInimigo(null);
                if (inimigo == null) {
                    BGM.stop();
                    return;
                }

                PLAYER_NIVEL = escolherNivel(null);
                if (PLAYER_NIVEL == -1) {
                    BGM.stop();
                    return;
                }

                aplicarMultiplicadoresPorNivel(PLAYER_NIVEL);

                int pvMaxAjustado = (int) Math.round(PLAYER_PV_MAX_BASE * PLAYER_HP_MULT);
                Combatente jogador = new Combatente("Você", pvMaxAjustado, PLAYER_PA_INICIO, PLAYER_PA_MAX);

                new GameFrame(jogador, inimigo);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // ===== Habilidade / Dano helpers =====
    public static int calcularDanoBasico(Random rng) {
        if (rng.nextDouble() > BASIC_HIT_CHANCE) return 0; // errou
        int danoBase = rand(rng, BASIC_DMG_MIN, BASIC_DMG_MAX);
        int dano = (int)Math.round(danoBase * PLAYER_DMG_MULT);
        boolean crit = rng.nextDouble() < BASIC_CRIT_CHANCE;
        if (crit) dano = (int)Math.round(dano * BASIC_CRIT_MULT);
        return dano;
    }

    public static boolean foiCritico(int danoAplicado, int pvAntesAlvo) {
        int baseMin = (int)Math.round(BASIC_DMG_MIN * PLAYER_DMG_MULT);
        return danoAplicado >= Math.round(baseMin * BASIC_CRIT_MULT)
                && danoAplicado > 0
                && danoAplicado <= pvAntesAlvo;
    }

    public static int calcularDanoHabilidade(Random rng, Habilidade h) {
        int danoBase = rand(rng, h.danoMin, h.danoMax);
        return (int)Math.round(danoBase * PLAYER_DMG_MULT);
    }

    // ===== Fuga helpers =====
    public static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Chance de fuga considerando nível, seu PV relativo e PV do inimigo. */
    public static double computeFleeChance(Combatente jogador, Inimigo inimigo) {
        double pBase = FLEE_BASE_CHANCE; // 50% base
        double pLvl  = 0.05 * (PLAYER_NIVEL - 1); // +5% por nível acima de 1
        double pYou  = 0.10 * ((double)jogador.pv / Math.max(1, jogador.pvMax)); // até +10% se cheio
        double pEn   = 0.20 * (1.0 - (double)inimigo.pv / Math.max(1, inimigo.pvMax)); // até +20% se inimigo baixo
        double p = pBase + pLvl + pYou + pEn;
        return clamp(p, FLEE_MIN, FLEE_MAX);
    }

    // ===== Música / Escolhas pré-batalha =====
    public static void iniciarMusicaComFallback() {
        // 1) tenta do classpath: /musica/arquivo.wav
        URL res = Jogobluzao.class.getResource(MUSIC_RESOURCE_PATH);
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

    public static Inimigo escolherInimigo(java.awt.Component parent) {
        String[] nomes = new String[BESTIARIO.length];
        for (int i = 0; i < BESTIARIO.length; i++) nomes[i] = BESTIARIO[i].nome;

        int escolha = showOptions(parent, "Selecionar Inimigo",
                "Escolha o inimigo para a batalha (mais serão adicionados no futuro):",
                nomes, 0);
        if (escolha == -1) return null;
        Inimigo base = BESTIARIO[escolha];
        return new Inimigo(base.nome, base.pvMax, base.danoMin, base.danoMax, base.chanceAcerto);
    }

    public static int escolherNivel(java.awt.Component parent) {
        String[] niveis = {"1", "2", "3", "4", "5"};
        int sel = showOptions(parent, "Selecionar Nível",
                "Escolha o nível do seu personagem (1 a 5):",
                niveis, 0);
        if (sel == -1) return -1;
        return sel + 1;
    }

    // ===== UTIL =====
    public static void aplicarMultiplicadoresPorNivel(int nivel) {
        PLAYER_HP_MULT = 1.0 + 0.20 * (nivel - 1);
        PLAYER_DMG_MULT = 1.0 + 0.15 * (nivel - 1);
    }

    public static int rand(Random rng, int min, int max) {
        return rng.nextInt((max - min) + 1) + min;
    }

    // Resumo simples do inventário para a HUD
    public static String inventarioResumo() {
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
    public static String montarHUD(Combatente jogador, Inimigo inimigo, String invResumo) {
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
            "     <div style='margin-top:8px; color:#555'>Acerto inimigo: " +
            (int)(inimigo.chanceAcerto*100) + "%</div>" +
            "   </td>" +
            " </tr>" +
            "</table>" +
            "<div style='margin-top:8px; font-size:12px; color:#555'>" +
            "  <b>Dicas:</b> Ataque Básico = +" + BASIC_PA_GANHO + " PA | Chance de acerto: " + acerto +
            " | Crítico: " + crit + " | Fuga: " + fuga +
            "</div>" +
            "<div style='margin-top:8px; font-size:12px; color:#555'>Inventário: " +
            esc(invResumo) + "</div>" +
            "</div>" +
            "</html>";
    }

    // --- Barras modernas (proporcionais e com cor dinâmica) ---
    public static String barHP(int cur, int max) {
        cur = Math.max(0, Math.min(cur, max));
        double ratio = (max == 0) ? 0 : (double) cur / max;

        Color fill;
        if (ratio > 0.66) fill = new Color(34, 197, 94);      // #22c55e
        else if (ratio > 0.33) fill = new Color(234, 179, 8); // #eab308
        else fill = new Color(239, 68, 68);                   // #ef4444

        return filledBar(ratio, colorHex(fill), "#111827");
    }

    public static String barPA(int cur, int max) {
        cur = Math.max(0, Math.min(cur, max));
        double ratio = (max == 0) ? 0 : (double) cur / max;

        Color start = new Color(59,130,246); // #3b82f6
        Color end   = new Color(124,58,237); // #7c3aed
        Color fill  = lerp(start, end, ratio);

        return filledBar(ratio, colorHex(fill), "#0b1220");
    }

    public static String barEnemy(Combatente jogador, Inimigo inimigo) {
        int cur = Math.max(0, Math.min(inimigo.pv, inimigo.pvMax));
        double ratio = (inimigo.pvMax == 0) ? 0 : (double) cur / inimigo.pvMax;

        Color high = new Color(239,68,68);   // #ef4444
        Color low  = new Color(127,29,29);   // #7f1d1d
        Color fill = lerp(low, high, ratio);

        return filledBar(ratio, colorHex(fill), "#1f0a0a");
    }

    public static String filledBar(double ratio, String fillHex, String backHex) {
        int pct = (int)Math.round(100 * Math.max(0, Math.min(1, ratio)));
        return "<div style='display:inline-block;width:220px;height:14px;" +
             "background:" + backHex + ";border-radius:6px;overflow:hidden;'>" +
             "<div style='width:" + pct + "%;height:100%;background:" + fillHex + ";'></div>" +
             "</div>";
    }

    public static Color lerp(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int)Math.round(a.getRed()   + (b.getRed()   - a.getRed())   * t);
        int g = (int)Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl= (int)Math.round(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t);
        return new Color(r,g,bl);
    }

    public static String colorHex(Color c) {
        return String.format("#%02x%02x%02x",
                c.getRed(), c.getGreen(), c.getBlue());
    }

    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public static int showOptions(java.awt.Component parent,
                                  String titulo,
                                  String mensagem,
                                  String[] opcoes,
                                  int defaultIndex) {
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
