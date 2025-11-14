import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;

public class GameFrame extends JFrame {

    // ====== CONFIG DE IMAGENS ======
    private static final String PLAYER_SPRITE_NORMAL_PATH = "Imagens/batalhanormal.png";
    private static final String PLAYER_SPRITE_HIT_PATH    = "Imagens/batalhanormal_hit.png"; // opcional
    private static final String ENEMY_SPRITE_PATH         = "Imagens/inimigo.png";           // troque p/ nome certo
    private static final String BACKGROUND_PATH           = "Imagens/cenario.png";           // troque p/ nome certo

    // ====== HUD MODES ======
    private enum HudMode { ACTIONS, HABS, ITENS }
    private HudMode hudMode = HudMode.ACTIONS;

    // ====== COMPONENTES UI ======
    final JTextArea log = new JTextArea(8, 40);

    final JButton btnAtk        = new JButton("ATAQUE");
    final JButton btnHabilidade = new JButton("HABILIDADE");
    final JButton btnItem       = new JButton("ITEM");
    final JButton btnFugir      = new JButton("FUGIR");
    final JButton btnMusica     = new JButton("MÚSICA ▶/⏸");

    // HUD de habilidades / itens
    final JButton btnVoltarHabs  = new JButton("VOLTAR");
    final JButton btnVoltarItens = new JButton("VOLTAR");
    final List<JButton> habButtons  = new ArrayList<>();
    final List<JButton> itemButtons = new ArrayList<>();

    final BattlePanel battlePanel;

    Combatente jogador;
    Inimigo inimigo;
    final Random rng = new Random();

    // ====== ANIMAÇÃO ======
    private Timer animTimer;
    int bgOffsetX = 0;
    double idlePhase = 0.0;

    boolean rotating = false;
    double rotationAngle = 0.0;
    int rotationTicks = 0;

    int playerDamageFlashFrames = 0;
    int enemyDamageFlashFrames  = 0;

    // ====== SPRITES / CENÁRIO ======
    Image backgroundImage;
    Image playerSpriteNormal;
    Image playerSpriteHit;    // sprite alternativa de dano
    Image enemySpriteImage;

    public GameFrame(Combatente jogador, Inimigo inimigo) {
        super("Jogobluzão — Batalha");
        this.jogador = jogador;
        this.inimigo = inimigo;

        // Carrega imagens
        backgroundImage    = loadImage(BACKGROUND_PATH);
        playerSpriteNormal = loadImage(PLAYER_SPRITE_NORMAL_PATH);
        playerSpriteHit    = loadImage(PLAYER_SPRITE_HIT_PATH);  // se não achar, fica null
        enemySpriteImage   = loadImage(ENEMY_SPRITE_PATH);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));
        getContentPane().setBackground(Color.black);

        // ===== PAINEL DE BATALHA =====
        battlePanel = new BattlePanel();
        battlePanel.setPreferredSize(new Dimension(800, 360));
        battlePanel.setLayout(null); // vamos posicionar os botões na mão
        add(battlePanel, BorderLayout.CENTER);

        // Botões principais (HUD de ações)
        for (JButton b : new JButton[]{btnAtk, btnHabilidade, btnItem, btnFugir, btnMusica}) {
            b.setFocusPainted(false);
            b.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            battlePanel.add(b);
        }

        // Botões de habilidades / itens
        btnVoltarHabs.setFocusPainted(false);
        btnVoltarItens.setFocusPainted(false);
        btnVoltarHabs.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnVoltarItens.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        battlePanel.add(btnVoltarHabs);
        battlePanel.add(btnVoltarItens);
        btnVoltarHabs.setVisible(false);
        btnVoltarItens.setVisible(false);

        // ===== LOG EMBAIXO =====
        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        log.setFont(new Font("Consolas", Font.PLAIN, 12));
        log.setBackground(new Color(10, 10, 18));
        log.setForeground(new Color(230, 230, 230));

        JScrollPane logScroll = new JScrollPane(log);
        logScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(60,60,80)),
                "Mensagens",
                0, 0,
                new Font("Segoe UI", Font.PLAIN, 11),
                new Color(220, 220, 240)
        ));

        add(logScroll, BorderLayout.SOUTH);

        // ===== AÇÕES DOS BOTÕES =====
        btnAtk.addActionListener(e -> onAtkBasico());

        btnHabilidade.addActionListener(e -> {
            hudMode = HudMode.HABS;
            rebuildHabilidadeButtons();
            battlePanel.revalidate();
            battlePanel.repaint();
        });

        btnItem.addActionListener(e -> {
            hudMode = HudMode.ITENS;
            rebuildItemButtons();
            battlePanel.revalidate();
            battlePanel.repaint();
        });

        btnFugir.addActionListener(e -> onFugir());
        btnMusica.addActionListener(e -> Jogobluzao.BGM.togglePause());

        btnVoltarHabs.addActionListener(e -> {
            hudMode = HudMode.ACTIONS;
            battlePanel.revalidate();
            battlePanel.repaint();
        });
        btnVoltarItens.addActionListener(e -> {
            hudMode = HudMode.ACTIONS;
            battlePanel.revalidate();
            battlePanel.repaint();
        });

        // Timer animação
        animTimer = new Timer(16, e -> {
            bgOffsetX += 2;
            if (backgroundImage != null) {
                int iw = backgroundImage.getWidth(null);
                if (iw > 0) bgOffsetX %= iw;
            }

            idlePhase += 0.08;

            if (rotating) {
                rotationAngle *= 0.90;
                rotationTicks--;
                if (rotationTicks <= 0 || Math.abs(rotationAngle) < 1.0) {
                    rotating = false;
                    rotationAngle = 0.0;
                }
            }

            if (playerDamageFlashFrames > 0) playerDamageFlashFrames--;
            if (enemyDamageFlashFrames > 0)  enemyDamageFlashFrames--;

            battlePanel.repaint();
        });
        animTimer.start();

        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        updateHUD();
        addLog("Um " + inimigo.nome + " apareceu!");
        addLog("Seu nível: " + Jogobluzao.PLAYER_NIVEL);
    }

    @Override
    public void dispose() {
        if (animTimer != null) animTimer.stop();
        super.dispose();
    }

    private Image loadImage(String path) {
        ImageIcon icon = new ImageIcon(path);
        if (icon.getIconWidth() <= 0 || icon.getIconHeight() <= 0) {
            System.err.println("Aviso: não foi possível carregar imagem: " + path);
            return null;
        }
        return icon.getImage();
    }

    // ===== HABILIDADES / ITENS HUD =====
    private void rebuildHabilidadeButtons() {
        // remove antigos
        for (JButton b : habButtons) {
            battlePanel.remove(b);
        }
        habButtons.clear();

        for (int i = 0; i < Jogobluzao.HABILIDADES.length; i++) {
            Habilidade h = Jogobluzao.HABILIDADES[i];

            // Danonão Grosso só a partir do nível 2
            if ("Danonão Grosso".equals(h.nome) && Jogobluzao.PLAYER_NIVEL < 2) continue;

            final int idx = i;
            JButton b = new JButton(h.nome + " (PA " + h.custoPA + ")");
            b.setFocusPainted(false);
            b.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            b.addActionListener(e -> {
                executarHabilidade(idx);
                hudMode = HudMode.ACTIONS;
                battlePanel.revalidate();
                battlePanel.repaint();
            });
            habButtons.add(b);
            battlePanel.add(b);
        }
    }

    private void rebuildItemButtons() {
        for (JButton b : itemButtons) {
            battlePanel.remove(b);
        }
        itemButtons.clear();

        for (int i = 0; i < Jogobluzao.INVENTARIO.length; i++) {
            Item it = Jogobluzao.INVENTARIO[i];
            if (it.qtd <= 0) continue;

            final int idx = i;
            JButton b = new JButton(it.nome + " (x" + it.qtd + ")");
            b.setFocusPainted(false);
            b.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            b.addActionListener(e -> {
                usarItem(idx);
                rebuildItemButtons(); // atualiza quantidades
                hudMode = HudMode.ACTIONS;
                battlePanel.revalidate();
                battlePanel.repaint();
            });
            itemButtons.add(b);
            battlePanel.add(b);
        }
    }

    // ===== Log / HUD =====
    void addLog(String s) {
        log.append(s + "\n");
        log.setCaretPosition(log.getDocument().getLength());
    }

    void updateHUD() {
        battlePanel.repaint();
    }

    void lockButtons(boolean lock) {
        btnAtk.setEnabled(!lock);
        btnHabilidade.setEnabled(!lock);
        btnItem.setEnabled(!lock);
        btnFugir.setEnabled(!lock);
        btnMusica.setEnabled(!lock);
        for (JButton b : habButtons) b.setEnabled(!lock);
        for (JButton b : itemButtons) b.setEnabled(!lock);
        btnVoltarHabs.setEnabled(!lock);
        btnVoltarItens.setEnabled(!lock);
    }

    // ====== Ações ======
    void onAtkBasico() {
        if (!jogador.vivo() || !inimigo.vivo()) return;

        int novoPA = Math.min(jogador.paMax, jogador.pa + Jogobluzao.BASIC_PA_GANHO);
        animatePAChangeAsync(jogador, novoPA);

        int dano = Jogobluzao.calcularDanoBasico(rng);
        if (dano <= 0) {
            addLog("Você atacou, mas ERROU! (+" + Jogobluzao.BASIC_PA_GANHO + " PA)");
            enemyTurnAfterDelay();
            return;
        }
        boolean crit = Jogobluzao.foiCritico(dano, inimigo.pv);
        int novoPV = Math.max(0, inimigo.pv - dano);
        triggerEnemyDamageFlash();
        animatePVChangeAsync(inimigo, novoPV);
        addLog((crit ? "Crítico! " : "") + "Você causou " + dano +
                " de dano! (+" + Jogobluzao.BASIC_PA_GANHO + " PA)");

        checkEndOrEnemyTurn();
    }

    void executarHabilidade(int idxHab) {
        if (!jogador.vivo() || !inimigo.vivo()) return;
        Habilidade h = Jogobluzao.HABILIDADES[idxHab];

        if (jogador.pa < h.custoPA) {
            addLog("PA insuficiente para " + h.nome + " (precisa " + h.custoPA + ").");
            return;
        }

        int novoPA = Math.max(0, jogador.pa - h.custoPA);
        animatePAChangeAsync(jogador, novoPA);

        int dano = Jogobluzao.calcularDanoHabilidade(rng, h);
        int novoPV = Math.max(0, inimigo.pv - dano);
        triggerEnemyDamageFlash();
        animatePVChangeAsync(inimigo, novoPV);

        if ("Danonão Grosso".equals(h.nome)) {
            double antes = inimigo.chanceAcerto;
            inimigo.chanceAcerto = Jogobluzao.clamp(antes - 0.20, 0.10, 0.95);

            int antesPct = (int) Math.round(antes * 100);
            int depoisPct = (int) Math.round(inimigo.chanceAcerto * 100);

            addLog("Danonão Grosso acertou em cheio! A chance de acerto de " +
                    inimigo.nome + " caiu de " + antesPct + "% para " + depoisPct + "%.");

            triggerSceneRotation();
        }

        addLog("Você usou " + h.nome + " e causou " + dano + " de dano!");

        checkEndOrEnemyTurn();
    }

    void usarItem(int idxItem) {
        Item escolhido = Jogobluzao.INVENTARIO[idxItem];
        if (escolhido.qtd <= 0) {
            addLog("Sem " + escolhido.nome + ".");
            return;
        }

        int antes = jogador.pv;
        int depois = Math.min(jogador.pvMax, jogador.pv + escolhido.cura);
        int curado = depois - antes;
        if (curado <= 0) {
            addLog("PV já está cheio.");
            return;
        }

        escolhido.qtd--;
        animatePVChangeAsync(jogador, depois);
        addLog("Você usou " + escolhido.nome + " e curou " + curado + " PV.");
    }

    void onFugir() {
        if (!jogador.vivo() || !inimigo.vivo()) return;

        double chance = Jogobluzao.computeFleeChance(jogador, inimigo);
        int pct = (int)Math.round(chance * 100);

        if (rng.nextDouble() < chance) {
            addLog("Você tentou fugir... SUCESSO (" + pct + "%).");
            lockButtons(true);
            dispose();
        } else {
            addLog("Você tentou fugir... FALHOU (" + pct + "%)!");
            enemyTurnAfterDelay();
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
        if (!jogador.vivo() || !inimigo.vivo()) {
            lockButtons(true);
            return;
        }
        if (rng.nextDouble() <= inimigo.chanceAcerto) {
            int dano = Jogobluzao.rand(rng, inimigo.danoMin, inimigo.danoMax);
            int novo = Math.max(0, jogador.pv - dano);
            triggerPlayerDamageFlash();
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

    // ====== EFEITOS VISUAIS ======
    void triggerSceneRotation() {
        rotating = true;
        rotationAngle = 18.0;
        rotationTicks = 30;
    }

    void triggerPlayerDamageFlash() {
        playerDamageFlashFrames = 18;
    }

    void triggerEnemyDamageFlash() {
        enemyDamageFlashFrames = 18;
    }

    // ====== Animações de PV/PA ======
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

    // ====== PAINEL DE BATALHA ======
    class BattlePanel extends JPanel {
        BattlePanel() { setOpaque(true); }

        @Override
        public void doLayout() {
            super.doLayout();

            int w = getWidth();
            int h = getHeight();

            // HUD GRANDE
            int hudWidth  = 520;
            int hudHeight = 180;
            int hudX = (w - hudWidth) / 2;
            int hudY = h - hudHeight - 30;
            int gap = 12;

            // === HUD DE AÇÕES ===
            if (hudMode == HudMode.ACTIONS) {
                // mostra botões principais
                btnAtk.setVisible(true);
                btnHabilidade.setVisible(true);
                btnItem.setVisible(true);
                btnFugir.setVisible(true);
                btnMusica.setVisible(true);

                // esconde HUDs secundárias
                btnVoltarHabs.setVisible(false);
                btnVoltarItens.setVisible(false);
                for (JButton b : habButtons) b.setVisible(false);
                for (JButton b : itemButtons) b.setVisible(false);

                int btnWidth  = (hudWidth - gap * 3) / 2;
                int btnHeight = 32;

                int row1Y = hudY + 50;
                int row2Y = row1Y + btnHeight + gap;

                btnAtk.setBounds(hudX + gap,                 row1Y, btnWidth, btnHeight);
                btnItem.setBounds(hudX + gap * 2 + btnWidth, row1Y, btnWidth, btnHeight);

                btnHabilidade.setBounds(hudX + gap,                 row2Y, btnWidth, btnHeight);
                btnFugir.setBounds(hudX + gap * 2 + btnWidth, row2Y, btnWidth, btnHeight);

                int musicW = 120;
                int musicH = 24;
                btnMusica.setBounds(hudX + hudWidth - musicW - gap,
                                    hudY + hudHeight - musicH - gap,
                                    musicW, musicH);

            } else if (hudMode == HudMode.HABS) {
                // esconde botões principais
                btnAtk.setVisible(false);
                btnHabilidade.setVisible(false);
                btnItem.setVisible(false);
                btnFugir.setVisible(false);
                btnMusica.setVisible(false);

                // mostra habilidades
                for (JButton b : habButtons) b.setVisible(true);
                for (JButton b : itemButtons) b.setVisible(false);
                btnVoltarItens.setVisible(false);
                btnVoltarHabs.setVisible(true);

                int btnWidth  = hudWidth - 2 * gap;
                int btnHeight = 28;
                int y = hudY + 48;
                for (JButton b : habButtons) {
                    b.setBounds(hudX + gap, y, btnWidth, btnHeight);
                    y += btnHeight + 6;
                }

                int backW = 100;
                int backH = 26;
                btnVoltarHabs.setBounds(hudX + hudWidth - backW - gap,
                                         hudY + hudHeight - backH - gap,
                                         backW, backH);

            } else if (hudMode == HudMode.ITENS) {
                // esconde principais
                btnAtk.setVisible(false);
                btnHabilidade.setVisible(false);
                btnItem.setVisible(false);
                btnFugir.setVisible(false);
                btnMusica.setVisible(false);

                // mostra itens
                for (JButton b : habButtons) b.setVisible(false);
                for (JButton b : itemButtons) b.setVisible(true);
                btnVoltarHabs.setVisible(false);
                btnVoltarItens.setVisible(true);

                int btnWidth  = hudWidth - 2 * gap;
                int btnHeight = 28;
                int y = hudY + 48;
                for (JButton b : itemButtons) {
                    b.setBounds(hudX + gap, y, btnWidth, btnHeight);
                    y += btnHeight + 6;
                }

                int backW = 100;
                int backH = 26;
                btnVoltarItens.setBounds(hudX + hudWidth - backW - gap,
                                         hudY + hudHeight - backH - gap,
                                         backW, backH);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth();
            int h = getHeight();

            g2.setColor(Color.black);
            g2.fillRect(0, 0, w, h);

            if (rotating) {
                g2.rotate(Math.toRadians(rotationAngle), w / 2.0, h / 2.0);
            }

            // ===== CENÁRIO =====
            if (backgroundImage != null) {
                int iw = backgroundImage.getWidth(null);
                int ih = backgroundImage.getHeight(null);
                if (iw > 0 && ih > 0) {
                    int y = 0;
                    while (y < h) {
                        int x = -bgOffsetX;
                        while (x < w) {
                            g2.drawImage(backgroundImage, x, y, null);
                            x += iw;
                        }
                        y += ih;
                    }
                }
            } else {
                GradientPaint gp = new GradientPaint(
                        0, 0, new Color(10, 10, 25),
                        0, h, new Color(5, 5, 10));
                g2.setPaint(gp);
                g2.fillRect(0, 0, w, h);
            }

            // ===== SPRITES =====
            int playerSpriteHeight = (int) (h * 0.80);
            int enemySpriteHeight  = (int) (h * 0.95); // inimigo maior
            int playerSpriteWidth  = playerSpriteHeight;
            int enemySpriteWidth   = enemySpriteHeight;

            int playerX = (int) (w * 0.18);
            int enemyX  = (int) (w * 0.82);

            int idleOffset = (int) (Math.sin(idlePhase) * 8);

            int baseTop = h - playerSpriteHeight + 30;
            int playerTopY = baseTop + idleOffset;
            int enemyTopY  = h - enemySpriteHeight + 30 - idleOffset;

            Image curPlayerSprite = playerSpriteNormal;
            if (playerDamageFlashFrames > 0 && playerSpriteHit != null) {
                curPlayerSprite = playerSpriteHit;
            }

            // PLAYER
            if (curPlayerSprite != null) {
                g2.drawImage(curPlayerSprite,
                        playerX - playerSpriteWidth / 2,
                        playerTopY,
                        playerSpriteWidth, playerSpriteHeight, null);
            } else {
                g2.setColor(new Color(40, 80, 140));
                g2.fillRoundRect(playerX - playerSpriteWidth/2, playerTopY,
                        playerSpriteWidth, playerSpriteHeight, 60, 60);
            }

            if (playerDamageFlashFrames > 0 && (playerDamageFlashFrames % 4 < 2)) {
                g2.setColor(new Color(255, 0, 0, 120));
                g2.fillRect(playerX - playerSpriteWidth/2, playerTopY,
                        playerSpriteWidth, playerSpriteHeight);
            }

            // INIMIGO (MAIOR)
            if (enemySpriteImage != null) {
                g2.drawImage(enemySpriteImage,
                        enemyX - enemySpriteWidth / 2,
                        enemyTopY,
                        enemySpriteWidth, enemySpriteHeight, null);
            } else {
                g2.setColor(new Color(140, 60, 60));
                g2.fillRoundRect(enemyX - enemySpriteWidth/2, enemyTopY,
                        enemySpriteWidth, enemySpriteHeight, 60, 60);
            }

            if (enemyDamageFlashFrames > 0 && (enemyDamageFlashFrames % 4 < 2)) {
                g2.setColor(new Color(255, 0, 0, 120));
                g2.fillRect(enemyX - enemySpriteWidth/2, enemyTopY,
                        enemySpriteWidth, enemySpriteHeight);
            }

            // barra de vida do inimigo
            drawEnemyHPBar(g2, enemyX, enemyTopY - 26);

            // HUD PV/PA + barra vermelha de vida
            drawPlayerHUD(g2, w, h);

            g2.dispose();
        }

        private void drawEnemyHPBar(Graphics2D g2, int centerX, int y) {
            int barWidth  = 220;
            int barHeight = 18;

            int cur = Math.max(0, Math.min(inimigo.pv, inimigo.pvMax));
            double ratio = (inimigo.pvMax == 0) ? 0 : (double) cur / inimigo.pvMax;
            int filled = (int) Math.round(barWidth * ratio);

            g2.setColor(new Color(15, 5, 10, 220));
            g2.fillRoundRect(centerX - barWidth / 2, y, barWidth, barHeight, 10, 10);

            Color high = new Color(239,68,68);
            Color low  = new Color(127,29,29);
            double t = Math.max(0, Math.min(1, ratio));
            int r = (int)Math.round(low.getRed()   + (high.getRed()   - low.getRed())   * t);
            int g = (int)Math.round(low.getGreen() + (high.getGreen() - low.getGreen()) * t);
            int b = (int)Math.round(low.getBlue()  + (high.getBlue()  - low.getBlue())  * t);
            g2.setColor(new Color(r,g,b));
            g2.fillRoundRect(centerX - barWidth / 2, y, filled, barHeight, 10, 10);

            g2.setColor(new Color(250, 220, 220));
            g2.drawRoundRect(centerX - barWidth / 2, y, barWidth, barHeight, 10, 10);

            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            String text = "PV " + cur + " / " + inimigo.pvMax;
            FontMetrics fm = g2.getFontMetrics();
            int tx = centerX - fm.stringWidth(text) / 2;
            int ty = y + barHeight - 4;
            g2.drawString(text, tx, ty);
        }

        private void drawPlayerHUD(Graphics2D g2, int w, int h) {
            int hudWidth  = 520;
            int hudHeight = 180;
            int hudX = (w - hudWidth) / 2;
            int hudY = h - hudHeight - 30;

            // ===== BARRA VERMELHA DE VIDA (EM CIMA DA HUD) =====
            int vidaBarWidth  = hudWidth;
            int vidaBarHeight = 20;
            int vidaBarX = hudX;
            int vidaBarY = hudY - vidaBarHeight - 6;

            int cur = Math.max(0, Math.min(jogador.pv, jogador.pvMax));
            double ratio = (jogador.pvMax == 0) ? 0 : (double) cur / jogador.pvMax;
            int filled = (int) Math.round(vidaBarWidth * ratio);

            g2.setColor(new Color(60, 10, 10, 230));
            g2.fillRoundRect(vidaBarX, vidaBarY, vidaBarWidth, vidaBarHeight, 10, 10);

            g2.setColor(new Color(220, 40, 40));
            g2.fillRoundRect(vidaBarX, vidaBarY, filled, vidaBarHeight, 10, 10);

            g2.setColor(new Color(250, 220, 220));
            g2.drawRoundRect(vidaBarX, vidaBarY, vidaBarWidth, vidaBarHeight, 10, 10);

            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            String txtVida = "VIDA: " + cur + " / " + jogador.pvMax;
            int txVida = vidaBarX + 8;
            int tyVida = vidaBarY + vidaBarHeight - 4;
            g2.drawString(txtVida, txVida, tyVida);

            // ===== CAIXA DA HUD =====
            g2.setColor(new Color(10, 10, 20, 230));
            g2.fillRoundRect(hudX, hudY, hudWidth, hudHeight, 14, 14);
            g2.setColor(new Color(200, 200, 230));
            g2.drawRoundRect(hudX, hudY, hudWidth, hudHeight, 14, 14);

            // Texto PV/PA dentro da HUD
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            String stats = "PV: " + jogador.pv + " / " + jogador.pvMax +
                    "    |    PA: " + jogador.pa + " / " + jogador.paMax;
            int statsX = hudX + 16;
            int statsY = hudY + 28;
            g2.drawString(stats, statsX, statsY);
        }
    }
}
