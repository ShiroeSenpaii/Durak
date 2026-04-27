package com.martyplex.durakroguelike;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().getInsetsController().hide(WindowInsets.Type.statusBars());
        setContentView(new DurakGameView(this));
    }

    public static class DurakGameView extends View {
        private static final String TAG = "DurakGame";

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Handler uiHandler = new Handler(Looper.getMainLooper());

        private final GameState gameState = new GameState();
        private final Random random = new Random(System.currentTimeMillis());

        private final List<RectF> playerCardRects = new ArrayList<>();
        private final List<ButtonHitbox> buttons = new ArrayList<>();
        private final List<FloatingText> floatingTexts = new ArrayList<>();

        private float density;
        private int selectedCardIndex = -1;

        private boolean botThinking;
        private boolean cardSharkUsed;
        private boolean oldMasterUsed;
        private boolean glareUsed;
        private int greasyObscuredIndex = -1;
        private int failedDefenseCount;

        private Card activeAttackCard;
        private int activeAttacker;
        private int activeDefender;
        private final List<Card> tableCards = new ArrayList<>();
        private final Set<Integer> tableRanks = new HashSet<>();

        public DurakGameView(Context context) {
            super(context);
            density = getResources().getDisplayMetrics().density;
            textPaint.setColor(Color.parseColor("#F4E7CC"));
            textPaint.setTextAlign(Paint.Align.LEFT);
            initRun();
        }

        private void initRun() {
            gameState.phase = GamePhase.TITLE;
            gameState.reputation = 72;
            gameState.coins = 0;
            gameState.round = 1;
            gameState.luckyFoolSaved = false;
            gameState.relics.clear();
            selectedCardIndex = -1;
            buttons.clear();
            botThinking = false;
            cardSharkUsed = false;
            oldMasterUsed = false;
            glareUsed = false;
            failedDefenseCount = 0;
            gameState.logMessage = "Welcome to FOOL'S TABLE.";
            Log.d(TAG, "New run initialized.");
        }

        private void startEncounter() {
            gameState.phase = GamePhase.TABLE;
            gameState.players.clear();
            gameState.players.add(new Player("You", false, BotPersonality.PLAYER));
            gameState.players.add(new Player("The Hoarder", true, BotPersonality.HOARDER));
            gameState.players.add(new Player("The Bully", true, BotPersonality.BULLY));
            gameState.players.add(new Player("The Cleaner", true, BotPersonality.CLEANER));

            Deck deck = new Deck();
            deck.fill36();
            deck.shuffle(random);
            gameState.deck = deck;
            gameState.discardCount = 0;
            gameState.trumpSuit = deck.peekBottomSuit();
            gameState.nextDrawPreview = deck.peekTop();

            for (int i = 0; i < 6; i++) {
                for (Player player : gameState.players) {
                    Card c = deck.draw();
                    if (c != null) player.hand.add(c);
                }
            }

            gameState.currentModifier = rollModifier(gameState.round);
            if (gameState.round == 5) {
                gameState.currentBoss = BossType.values()[random.nextInt(BossType.values().length)];
                gameState.currentModifier = TableModifier.BOSS_TABLE;
            } else {
                gameState.currentBoss = null;
            }

            greasyObscuredIndex = -1;
            if (gameState.currentModifier == TableModifier.GREASY_CARDS && !gameState.players.get(0).hand.isEmpty()) {
                greasyObscuredIndex = random.nextInt(gameState.players.get(0).hand.size());
            }

            activeAttacker = random.nextInt(4);
            activeDefender = (activeAttacker + 1) % 4;
            activeAttackCard = null;
            tableCards.clear();
            tableRanks.clear();
            selectedCardIndex = -1;
            cardSharkUsed = false;
            oldMasterUsed = false;
            glareUsed = false;
            failedDefenseCount = 0;
            gameState.logMessage = modifierIntro();
            Log.d(TAG, "Encounter started. Round " + gameState.round + " trump=" + gameState.trumpSuit);
            scheduleBotTurnIfNeeded();
            invalidate();
        }

        private String modifierIntro() {
            if (gameState.currentModifier == TableModifier.BOSS_TABLE) {
                return "Boss table: " + gameState.currentBoss.title;
            }
            return "Modifier: " + gameState.currentModifier.label;
        }

        private TableModifier rollModifier(int round) {
            List<TableModifier> pool = new ArrayList<>();
            pool.add(TableModifier.NORMAL);
            pool.add(TableModifier.GREASY_CARDS);
            pool.add(TableModifier.FAMILY_TABLE);
            if (round >= 2) pool.add(TableModifier.NO_MERCY);
            if (round >= 3) pool.add(TableModifier.WEDDING_TABLE);
            return pool.get(random.nextInt(pool.size()));
        }

        private void scheduleBotTurnIfNeeded() {
            if (gameState.phase != GamePhase.TABLE || botThinking) return;
            Player current = gameState.players.get(activeAttacker);
            Player defender = gameState.players.get(activeDefender);
            if (!current.isBot && activeAttackCard == null) return;
            if (!defender.isBot && activeAttackCard != null) return;

            botThinking = true;
            uiHandler.postDelayed(() -> {
                botThinking = false;
                executeBotStep();
                invalidate();
            }, 550);
        }

        private void executeBotStep() {
            if (gameState.phase != GamePhase.TABLE) return;
            Player attacker = gameState.players.get(activeAttacker);
            Player defender = gameState.players.get(activeDefender);

            if (activeAttackCard == null) {
                if (attacker.hand.isEmpty()) {
                    rotateRoles();
                    return;
                }
                Card attack = chooseAttackCard(attacker);
                if (attack == null) {
                    rotateRoles();
                    return;
                }
                attacker.hand.remove(attack);
                activeAttackCard = attack;
                tableCards.add(attack);
                tableRanks.add(attack.rank);
                gameState.logMessage = attacker.name + " attacks with " + attack.label();
                Log.d(TAG, gameState.logMessage);
                scheduleBotTurnIfNeeded();
                return;
            }

            if (defender.isBot) {
                Card defend = chooseDefendCard(defender, activeAttackCard);
                if (defend == null) {
                    onDefenderTake(defender, false);
                } else {
                    defender.hand.remove(defend);
                    resolveDefense(defender, defend, false);
                }
            } else {
                // Wait for player touch input.
            }
        }

        private Card chooseAttackCard(Player attacker) {
            List<Card> cards = new ArrayList<>(attacker.hand);
            cards.sort(Comparator.comparingInt(c -> c.effectiveRank(gameState, false)));
            if (attacker.personality == BotPersonality.BULLY) {
                cards.sort((a, b) -> Integer.compare(b.effectiveRank(gameState, false), a.effectiveRank(gameState, false)));
            } else if (attacker.personality == BotPersonality.HOARDER) {
                cards.sort((a, b) -> {
                    boolean at = a.suit == gameState.trumpSuit;
                    boolean bt = b.suit == gameState.trumpSuit;
                    if (at == bt) return Integer.compare(a.rank, b.rank);
                    return at ? 1 : -1;
                });
            } else if (attacker.personality == BotPersonality.IDIOT_GENIUS && random.nextFloat() < 0.4f) {
                return cards.get(random.nextInt(cards.size()));
            }
            return cards.isEmpty() ? null : cards.get(0);
        }

        private Card chooseDefendCard(Player defender, Card attack) {
            List<Card> valid = validDefenses(defender.hand, attack);
            if (valid.isEmpty()) return null;
            valid.sort(Comparator.comparingInt(c -> c.effectiveRank(gameState, true)));

            if (defender.personality == BotPersonality.HOARDER) {
                valid.sort((a, b) -> {
                    boolean at = isCardTrump(a);
                    boolean bt = isCardTrump(b);
                    if (at == bt) return Integer.compare(a.effectiveRank(gameState, true), b.effectiveRank(gameState, true));
                    return at ? 1 : -1;
                });
            } else if (defender.personality == BotPersonality.BULLY) {
                if (random.nextFloat() < 0.25f) return null;
                valid.sort((a, b) -> Integer.compare(b.effectiveRank(gameState, true), a.effectiveRank(gameState, true)));
            } else if (defender.personality == BotPersonality.IDIOT_GENIUS && random.nextFloat() < 0.45f) {
                return valid.get(random.nextInt(valid.size()));
            }
            return valid.get(0);
        }

        private void resolveDefense(Player defender, Card defense, boolean byPlayer) {
            tableCards.add(defense);
            tableRanks.add(defense.rank);
            gameState.discardCount += 2;
            gameState.logMessage = defender.name + " defended with " + defense.label();
            if (byPlayer && gameState.selectedClass == CharacterClass.OLD_MASTER && !oldMasterUsed) {
                oldMasterUsed = true;
                changeReputation(3, "Old Master steadies your nerves +3 Reputation");
            }
            maybeThrowIn();
            activeAttackCard = null;
            tableCards.clear();
            tableRanks.clear();
            refillHands();
            checkEncounterEnd();
            rotateRoles();
            Log.d(TAG, gameState.logMessage);
            scheduleBotTurnIfNeeded();
        }

        private void onDefenderTake(Player defender, boolean byPlayer) {
            int damage = byPlayer ? 8 : 2;
            if (gameState.currentModifier == TableModifier.FAMILY_TABLE) damage += 2;
            if (gameState.currentModifier == TableModifier.NO_MERCY) damage += 1;
            if (gameState.currentModifier == TableModifier.BOSS_TABLE && gameState.currentBoss == BossType.FINAL_FOOL) {
                failedDefenseCount++;
                if (failedDefenseCount >= 2) damage += 5;
            }

            if (gameState.relics.contains(Relic.POCKET_SIX) && hasRank(gameState.players.get(0).hand, 6)) {
                damage = Math.max(0, damage - 4);
                gameState.logMessage = "Pocket Six softens the blow.";
            }
            if (gameState.relics.contains(Relic.BORROWED_COAT)) {
                damage = Math.max(0, damage - 2);
            }

            if (byPlayer) {
                changeReputation(-damage, "You take cards. -" + damage + " Reputation");
            }

            defender.hand.addAll(tableCards);
            activeAttackCard = null;
            tableCards.clear();
            tableRanks.clear();
            refillHands();
            checkEncounterEnd();
            activeAttacker = (activeDefender + 1) % 4;
            activeDefender = (activeAttacker + 1) % 4;
            scheduleBotTurnIfNeeded();
        }

        private boolean hasRank(List<Card> cards, int rank) {
            for (Card c : cards) {
                if (c.rank == rank) return true;
            }
            return false;
        }

        private void maybeThrowIn() {
            if (tableRanks.isEmpty()) return;
            float chance = 0.18f;
            if (gameState.currentModifier == TableModifier.NO_MERCY) chance += 0.2f;
            if (gameState.currentModifier == TableModifier.WEDDING_TABLE) chance += 0.25f;
            if (gameState.currentModifier == TableModifier.BOSS_TABLE && gameState.currentBoss == BossType.WEDDING_TABLE_BOSS) {
                chance += 0.3f;
            }
            for (int idx = 1; idx < gameState.players.size(); idx++) {
                if (idx == activeAttacker || idx == activeDefender) continue;
                Player p = gameState.players.get(idx);
                if (p.hand.isEmpty()) continue;
                if (random.nextFloat() > chance) continue;
                if (gameState.relics.contains(Relic.GRANDMA_GLARE) && !glareUsed) {
                    glareUsed = true;
                    gameState.logMessage = "Grandma's Glare blocks a throw-in.";
                    return;
                }
                Card throwCard = null;
                for (Card c : p.hand) {
                    if (tableRanks.contains(c.rank)) {
                        throwCard = c;
                        break;
                    }
                }
                if (throwCard != null) {
                    p.hand.remove(throwCard);
                    tableCards.add(throwCard);
                    tableRanks.add(throwCard.rank);
                    gameState.logMessage = p.name + " throws in " + throwCard.label();
                    if (!gameState.players.get(activeDefender).isBot) {
                        activeAttackCard = throwCard;
                        return;
                    }
                }
            }
        }

        private void refillHands() {
            int current = activeAttacker;
            for (int i = 0; i < gameState.players.size(); i++) {
                Player p = gameState.players.get((current + i) % gameState.players.size());
                while (p.hand.size() < 6) {
                    Card c = gameState.deck.draw();
                    if (c == null) break;
                    p.hand.add(c);
                }
            }
            gameState.nextDrawPreview = gameState.deck.peekTop();
        }

        private void rotateRoles() {
            activeAttacker = (activeAttacker + 1) % gameState.players.size();
            activeDefender = (activeAttacker + 1) % gameState.players.size();
        }

        private void checkEncounterEnd() {
            Player human = gameState.players.get(0);
            boolean othersHaveCards = false;
            for (int i = 1; i < gameState.players.size(); i++) {
                if (!gameState.players.get(i).hand.isEmpty()) {
                    othersHaveCards = true;
                    break;
                }
            }

            if (human.hand.isEmpty() && othersHaveCards) {
                int reward = 16 + (gameState.round * 4);
                if (gameState.selectedClass == CharacterClass.MARKET_HUSTLER) reward += reward / 4;
                if (gameState.relics.contains(Relic.BUS_STOP_LUCK) && human.hand.size() == 0) reward += 6;
                if (gameState.relics.contains(Relic.VODKA_CONFIDENCE)) reward += 5;
                gameState.coins += reward;
                addFloating("+" + reward + " coins", true);
                if (gameState.round >= 5) {
                    gameState.phase = GamePhase.VICTORY;
                    gameState.logMessage = "You cleared the final table.";
                } else {
                    prepareRelicChoices();
                    gameState.phase = GamePhase.REWARD;
                    gameState.logMessage = "Table won. Pick a relic.";
                }
                Log.d(TAG, "Encounter won");
            } else if (!human.hand.isEmpty() && !othersHaveCards && gameState.deck.isEmpty()) {
                changeReputation(-12, "You were last with cards. -12 Reputation");
                onEncounterLost();
            }

            if (gameState.reputation <= 0) {
                gameState.phase = GamePhase.RUN_OVER;
                gameState.logMessage = "Your reputation collapsed.";
            }
        }

        private void onEncounterLost() {
            if (gameState.reputation <= 0) {
                gameState.phase = GamePhase.RUN_OVER;
                return;
            }
            if (gameState.round >= 5) {
                gameState.phase = GamePhase.RUN_OVER;
                gameState.logMessage = "The boss table crushed you.";
            } else {
                gameState.phase = GamePhase.SHOP;
                gameState.logMessage = "You limp to the market.";
            }
        }

        private void prepareRelicChoices() {
            gameState.offeredRelics.clear();
            List<Relic> pool = new ArrayList<>(Arrays.asList(Relic.values()));
            pool.removeAll(gameState.relics);
            Collections.shuffle(pool, random);
            for (int i = 0; i < Math.min(3, pool.size()); i++) {
                gameState.offeredRelics.add(pool.get(i));
            }
        }

        private void addFloating(String text, boolean positive) {
            floatingTexts.add(new FloatingText(text, getWidth() * 0.5f, getHeight() * 0.45f, positive));
        }

        private void changeReputation(int delta, String reason) {
            int old = gameState.reputation;
            gameState.reputation += delta;
            if (gameState.selectedClass == CharacterClass.LUCKY_FOOL && !gameState.luckyFoolSaved && old > 0 && gameState.reputation <= 0) {
                gameState.luckyFoolSaved = true;
                gameState.reputation = 1;
                reason = "Lucky Fool clings to 1 Reputation.";
            }
            gameState.reputation = Math.max(0, Math.min(100, gameState.reputation));
            gameState.logMessage = reason;
            addFloating((delta >= 0 ? "+" : "") + delta + " rep", delta >= 0);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            updateFloating();
            drawBackground(canvas);
            buttons.clear();

            switch (gameState.phase) {
                case TITLE:
                    drawTitle(canvas);
                    break;
                case CHARACTER_SELECT:
                    drawCharacterSelect(canvas);
                    break;
                case TABLE:
                    drawTable(canvas);
                    break;
                case REWARD:
                    drawReward(canvas);
                    break;
                case SHOP:
                    drawShop(canvas);
                    break;
                case RUN_OVER:
                    drawEnd(canvas, false);
                    break;
                case VICTORY:
                    drawEnd(canvas, true);
                    break;
            }

            for (FloatingText f : floatingTexts) {
                paint.setColor(f.positive ? Color.parseColor("#DFC15C") : Color.parseColor("#C95C5C"));
                paint.setTextSize(dp(16));
                paint.setAlpha((int) (255 * f.life));
                canvas.drawText(f.text, f.x, f.y, paint);
            }
            paint.setAlpha(255);
        }

        private void drawBackground(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            paint.setShader(new LinearGradient(0, 0, 0, h,
                    Color.parseColor("#120A08"), Color.parseColor("#2D1811"), Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(new RadialGradient(w * 0.5f, h * 0.45f, w * 0.45f,
                    new int[]{Color.parseColor("#66482D"), Color.parseColor("#11111100")},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(w * 0.5f, h * 0.45f, w * 0.45f, paint);
            paint.setShader(null);
        }

        private void drawTitle(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(Color.parseColor("#D4AF37"));
            paint.setTextSize(dp(52));
            canvas.drawText("FOOL'S", w / 2, h * 0.24f, paint);
            canvas.drawText("TABLE", w / 2, h * 0.32f, paint);
            paint.setTextSize(dp(16));
            paint.setColor(Color.parseColor("#F4E7CC"));
            canvas.drawText("Durak Roguelike", w / 2, h * 0.37f, paint);
            drawPanel(canvas, w * 0.1f, h * 0.45f, w * 0.9f, h * 0.7f);
            paint.setTextSize(dp(17));
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("• 3 AI table opponents", w * 0.14f, h * 0.52f, paint);
            canvas.drawText("• Roguelike relic progression", w * 0.14f, h * 0.57f, paint);
            canvas.drawText("• Survive to Round 5 boss", w * 0.14f, h * 0.62f, paint);
            addButton(canvas, "Start Run", w * 0.28f, h * 0.76f, w * 0.72f, h * 0.84f, this::onStartRun);
        }

        private void onStartRun() {
            initRun();
            gameState.phase = GamePhase.CHARACTER_SELECT;
        }

        private void drawCharacterSelect(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(30));
            paint.setColor(Color.parseColor("#D4AF37"));
            canvas.drawText("Choose Your Fool", w / 2, h * 0.13f, paint);

            CharacterClass[] classes = CharacterClass.values();
            float top = h * 0.2f;
            float boxH = h * 0.15f;
            for (int i = 0; i < classes.length; i++) {
                CharacterClass cc = classes[i];
                float y1 = top + i * (boxH + dp(12));
                float y2 = y1 + boxH;
                drawPanel(canvas, w * 0.08f, y1, w * 0.92f, y2);
                paint.setColor(Color.parseColor("#F4E7CC"));
                paint.setTextAlign(Paint.Align.LEFT);
                paint.setTextSize(dp(22));
                canvas.drawText(cc.title, w * 0.12f, y1 + dp(28), paint);
                paint.setTextSize(dp(14));
                canvas.drawText(cc.effect, w * 0.12f, y1 + dp(52), paint);
                addButton(canvas, cc.title, w * 0.08f, y1, w * 0.92f, y2, () -> onCharacterSelected(cc));
            }
        }

        private void onCharacterSelected(CharacterClass cc) {
            gameState.selectedClass = cc;
            gameState.reputation = cc == CharacterClass.LUCKY_FOOL ? 62 : 72;
            gameState.logMessage = cc.title + " enters the tavern.";
            startEncounter();
        }

        private void drawTable(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            drawPanel(canvas, w * 0.03f, h * 0.03f, w * 0.97f, h * 0.18f);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setColor(Color.parseColor("#F4E7CC"));
            paint.setTextSize(dp(14));
            canvas.drawText("Rep: " + gameState.reputation + "/100", w * 0.06f, h * 0.08f, paint);
            canvas.drawText("Coins: " + gameState.coins, w * 0.06f, h * 0.11f, paint);
            canvas.drawText("Round: " + gameState.round + "  " + gameState.currentModifier.label, w * 0.06f, h * 0.14f, paint);
            canvas.drawText("Trump: " + gameState.trumpSuit.symbol + "  Deck: " + gameState.deck.size(), w * 0.52f, h * 0.08f, paint);
            canvas.drawText("Atk: " + gameState.players.get(activeAttacker).name, w * 0.52f, h * 0.11f, paint);
            canvas.drawText("Def: " + gameState.players.get(activeDefender).name, w * 0.52f, h * 0.14f, paint);

            drawAvatars(canvas);
            drawCenterTable(canvas);
            drawPlayerHand(canvas);
            drawButtons(canvas);

            paint.setColor(Color.parseColor("#EAD8B3"));
            paint.setTextSize(dp(13));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(gameState.logMessage, w * 0.5f, h * 0.63f, paint);

            if (gameState.relics.contains(Relic.MARKED_DECK) && gameState.nextDrawPreview != null) {
                canvas.drawText("Next draw: " + gameState.nextDrawPreview.label(), w * 0.5f, h * 0.66f, paint);
            }
        }

        private void drawAvatars(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float[][] pos = {
                    {w * 0.5f, h * 0.26f},
                    {w * 0.2f, h * 0.32f},
                    {w * 0.8f, h * 0.32f}
            };
            for (int i = 1; i <= 3; i++) {
                Player p = gameState.players.get(i);
                float x = pos[i - 1][0];
                float y = pos[i - 1][1];
                paint.setColor(Color.parseColor(i == activeAttacker ? "#A0292A" : "#4B2F24"));
                canvas.drawCircle(x, y, dp(32), paint);
                paint.setColor(Color.parseColor("#F4E7CC"));
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(dp(12));
                canvas.drawText("●●", x, y + dp(3), paint);
                canvas.drawText(p.name, x, y + dp(50), paint);
                canvas.drawText("Cards: " + p.hand.size(), x, y + dp(66), paint);
            }
        }

        private void drawCenterTable(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            RectF table = new RectF(w * 0.1f, h * 0.27f, w * 0.9f, h * 0.6f);
            paint.setColor(Color.parseColor("#3B2418"));
            canvas.drawRoundRect(table, dp(20), dp(20), paint);

            if (activeAttackCard != null) {
                drawCard(canvas, activeAttackCard, w * 0.39f, h * 0.4f, dp(84), dp(120), false);
            }
            if (tableCards.size() >= 2) {
                drawCard(canvas, tableCards.get(1), w * 0.52f, h * 0.42f, dp(84), dp(120), false);
            }
        }

        private void drawPlayerHand(Canvas canvas) {
            playerCardRects.clear();
            Player human = gameState.players.get(0);
            float w = getWidth();
            float h = getHeight();
            float cardW = dp(64);
            float cardH = dp(96);
            float gap = dp(10);
            float total = human.hand.size() * cardW + Math.max(0, human.hand.size() - 1) * gap;
            float startX = Math.max(dp(8), (w - total) / 2f);
            float y = h * 0.72f;

            for (int i = 0; i < human.hand.size(); i++) {
                float x = startX + i * (cardW + gap);
                float yy = y - (selectedCardIndex == i ? dp(16) : 0);
                Card c = human.hand.get(i);
                boolean hidden = gameState.currentModifier == TableModifier.GREASY_CARDS && i == greasyObscuredIndex && selectedCardIndex != i;
                boolean playable = isPlayerCardPlayable(c);
                drawCard(canvas, c, x, yy, cardW, cardH, hidden);
                if (!playable) {
                    paint.setColor(Color.parseColor("#99000000"));
                    canvas.drawRoundRect(new RectF(x, yy, x + cardW, yy + cardH), dp(8), dp(8), paint);
                }
                playerCardRects.add(new RectF(x, yy, x + cardW, yy + cardH));
            }
        }

        private boolean isPlayerCardPlayable(Card c) {
            if (gameState.phase != GamePhase.TABLE) return false;
            if (activeAttackCard == null) {
                return activeAttacker == 0;
            }
            if (activeDefender == 0) {
                return canDefend(c, activeAttackCard);
            }
            return false;
        }

        private void drawButtons(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float bw = w * 0.2f;
            float bh = dp(44);
            float y = h * 0.88f;

            addButton(canvas, "ATTACK", w * 0.04f, y, w * 0.04f + bw, y + bh, this::onAttackTap);
            addButton(canvas, "DEFEND", w * 0.28f, y, w * 0.28f + bw, y + bh, this::onDefendTap);
            addButton(canvas, "TAKE", w * 0.52f, y, w * 0.52f + bw, y + bh, this::onTakeTap);
            addButton(canvas, "PASS", w * 0.76f, y, w * 0.76f + bw, y + bh, this::onPassTap);
            if (gameState.selectedClass == CharacterClass.CARD_SHARK && !cardSharkUsed) {
                addButton(canvas, "PEEK", w * 0.76f, y - dp(52), w * 0.76f + bw, y - dp(8), this::onPeekTap);
            }
        }

        private void onPeekTap() {
            if (cardSharkUsed || gameState.phase != GamePhase.TABLE) return;
            List<Player> enemies = gameState.players.subList(1, gameState.players.size());
            List<Player> valid = new ArrayList<>();
            for (Player p : enemies) if (!p.hand.isEmpty()) valid.add(p);
            if (valid.isEmpty()) return;
            Player pick = valid.get(random.nextInt(valid.size()));
            Card card = pick.hand.get(random.nextInt(pick.hand.size()));
            cardSharkUsed = true;
            gameState.logMessage = "Card Shark reveals " + pick.name + " holds " + card.label();
        }

        private void onAttackTap() {
            if (gameState.phase != GamePhase.TABLE || activeAttacker != 0 || selectedCardIndex < 0) return;
            Player human = gameState.players.get(0);
            if (selectedCardIndex >= human.hand.size()) return;
            Card c = human.hand.remove(selectedCardIndex);
            if (gameState.relics.contains(Relic.BENT_ACE) && c.rank == 14) c.forceTrumpThisTable = true;
            activeAttackCard = c;
            tableCards.add(c);
            tableRanks.add(c.rank);
            gameState.logMessage = "You attack with " + c.label();
            selectedCardIndex = -1;
            scheduleBotTurnIfNeeded();
        }

        private void onDefendTap() {
            if (gameState.phase != GamePhase.TABLE || activeDefender != 0 || selectedCardIndex < 0 || activeAttackCard == null)
                return;
            Player human = gameState.players.get(0);
            if (selectedCardIndex >= human.hand.size()) return;
            Card c = human.hand.get(selectedCardIndex);
            if (!canDefend(c, activeAttackCard)) {
                gameState.logMessage = "That card cannot defend.";
                return;
            }
            human.hand.remove(selectedCardIndex);
            selectedCardIndex = -1;
            resolveDefense(human, c, true);
        }

        private void onTakeTap() {
            if (gameState.phase != GamePhase.TABLE || activeDefender != 0 || activeAttackCard == null) return;
            onDefenderTake(gameState.players.get(0), true);
        }

        private void onPassTap() {
            if (gameState.phase != GamePhase.TABLE) return;
            if (activeAttackCard != null) {
                gameState.logMessage = "Pass not available now.";
                return;
            }
            if (activeAttacker == 0 || activeDefender == 0) {
                rotateRoles();
                gameState.logMessage = "You pass.";
                scheduleBotTurnIfNeeded();
            }
        }

        private List<Card> validDefenses(List<Card> hand, Card attack) {
            List<Card> out = new ArrayList<>();
            for (Card c : hand) if (canDefend(c, attack)) out.add(c);
            return out;
        }

        private boolean canDefend(Card defense, Card attack) {
            boolean dTrump = isCardTrump(defense);
            boolean aTrump = isCardTrump(attack);
            int dRank = defense.effectiveRank(gameState, true);
            int aRank = attack.effectiveRank(gameState, false);
            if (defense.suit == attack.suit && dRank > aRank) return true;
            if (!aTrump && dTrump) return true;
            return aTrump && dTrump && dRank > aRank;
        }

        private boolean isCardTrump(Card card) {
            return card.forceTrumpThisTable || card.suit == gameState.trumpSuit;
        }

        private void drawReward(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(30));
            paint.setColor(Color.parseColor("#D4AF37"));
            canvas.drawText("Choose a Relic", w * 0.5f, h * 0.14f, paint);

            for (int i = 0; i < gameState.offeredRelics.size(); i++) {
                Relic relic = gameState.offeredRelics.get(i);
                float y1 = h * 0.22f + i * h * 0.22f;
                float y2 = y1 + h * 0.18f;
                drawPanel(canvas, w * 0.08f, y1, w * 0.92f, y2);
                paint.setTextAlign(Paint.Align.LEFT);
                paint.setColor(Color.parseColor("#F4E7CC"));
                paint.setTextSize(dp(22));
                canvas.drawText(relic.title, w * 0.12f, y1 + dp(30), paint);
                paint.setTextSize(dp(14));
                canvas.drawText(relic.effect, w * 0.12f, y1 + dp(56), paint);
                addButton(canvas, relic.title, w * 0.08f, y1, w * 0.92f, y2, () -> onRelicPicked(relic));
            }
        }

        private void onRelicPicked(Relic relic) {
            gameState.relics.add(relic);
            gameState.phase = GamePhase.SHOP;
            gameState.logMessage = "Picked relic: " + relic.title;
        }

        private void drawShop(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            drawPanel(canvas, w * 0.08f, h * 0.16f, w * 0.92f, h * 0.72f);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(Color.parseColor("#D4AF37"));
            paint.setTextSize(dp(34));
            canvas.drawText("Market & Event", w * 0.5f, h * 0.24f, paint);

            int healCost = gameState.selectedClass == CharacterClass.MARKET_HUSTLER ? 10 : 12;
            paint.setColor(Color.parseColor("#F4E7CC"));
            paint.setTextSize(dp(16));
            canvas.drawText("Coins: " + gameState.coins + " | Reputation: " + gameState.reputation, w * 0.5f, h * 0.32f, paint);
            canvas.drawText("Buy hot soup (+8 Reputation) for " + healCost + " coins", w * 0.5f, h * 0.37f, paint);
            canvas.drawText("Or press Continue to next table", w * 0.5f, h * 0.41f, paint);

            addButton(canvas, "Buy Soup", w * 0.2f, h * 0.5f, w * 0.8f, h * 0.58f, () -> {
                if (gameState.coins >= healCost) {
                    gameState.coins -= healCost;
                    changeReputation(8, "Hot soup restores you.");
                } else {
                    gameState.logMessage = "Not enough coins.";
                }
            });
            addButton(canvas, "Continue", w * 0.2f, h * 0.62f, w * 0.8f, h * 0.7f, () -> {
                gameState.round++;
                if (gameState.round > 5) {
                    gameState.phase = GamePhase.VICTORY;
                } else {
                    startEncounter();
                }
            });
        }

        private void drawEnd(Canvas canvas, boolean victory) {
            float w = getWidth();
            float h = getHeight();
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(victory ? Color.parseColor("#D4AF37") : Color.parseColor("#B84D4D"));
            paint.setTextSize(dp(46));
            canvas.drawText(victory ? "VICTORY" : "RUN OVER", w * 0.5f, h * 0.3f, paint);
            paint.setColor(Color.parseColor("#F4E7CC"));
            paint.setTextSize(dp(18));
            canvas.drawText("Rounds cleared: " + gameState.round, w * 0.5f, h * 0.4f, paint);
            canvas.drawText("Relics: " + gameState.relics.size() + "  Coins: " + gameState.coins, w * 0.5f, h * 0.45f, paint);
            addButton(canvas, "New Run", w * 0.28f, h * 0.62f, w * 0.72f, h * 0.7f, this::onStartRun);
        }

        private void addButton(Canvas canvas, String label, float x1, float y1, float x2, float y2, Runnable action) {
            paint.setColor(Color.parseColor("#912D2D"));
            RectF rect = new RectF(x1, y1, x2, y2);
            paint.setColor(Color.parseColor("#912D2D"));
            canvas.drawRoundRect(rect, dp(10), dp(10), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.parseColor("#D4AF37"));
            canvas.drawRoundRect(rect, dp(10), dp(10), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#F4E7CC"));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(14));
            canvas.drawText(label, rect.centerX(), rect.centerY() + dp(5), paint);
            buttons.add(new ButtonHitbox(rect, action));
        }

        private void drawPanel(Canvas canvas, float x1, float y1, float x2, float y2) {
            paint.setColor(Color.parseColor("#AA2C1D14"));
            canvas.drawRoundRect(new RectF(x1, y1, x2, y2), dp(12), dp(12), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.parseColor("#9B7A39"));
            canvas.drawRoundRect(new RectF(x1, y1, x2, y2), dp(12), dp(12), paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawCard(Canvas canvas, Card card, float x, float y, float w, float h, boolean hidden) {
            paint.setColor(Color.parseColor("#E8D9BA"));
            canvas.drawRoundRect(new RectF(x, y, x + w, y + h), dp(8), dp(8), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.parseColor("#6D4B2E"));
            canvas.drawRoundRect(new RectF(x, y, x + w, y + h), dp(8), dp(8), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor(hidden ? "#6B5A45" : "#2A1A14"));
            paint.setTextSize(dp(18));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(hidden ? "?" : card.rankLabel(), x + w / 2, y + h * 0.38f, paint);
            paint.setTextSize(dp(16));
            paint.setColor(hidden ? Color.parseColor("#3D3028") : card.suit.color);
            canvas.drawText(hidden ? "✶" : card.suit.symbol, x + w / 2, y + h * 0.68f, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_DOWN) return true;
            float x = event.getX();
            float y = event.getY();

            for (ButtonHitbox b : buttons) {
                if (b.rect.contains(x, y)) {
                    b.action.run();
                    invalidate();
                    return true;
                }
            }

            if (gameState.phase == GamePhase.TABLE) {
                for (int i = 0; i < playerCardRects.size(); i++) {
                    if (playerCardRects.get(i).contains(x, y)) {
                        selectedCardIndex = i;
                        invalidate();
                        return true;
                    }
                }
            }
            return true;
        }

        private void updateFloating() {
            List<FloatingText> expired = new ArrayList<>();
            for (FloatingText f : floatingTexts) {
                f.y -= dp(0.7f);
                f.life -= 0.02f;
                if (f.life <= 0) expired.add(f);
            }
            floatingTexts.removeAll(expired);
        }

        private float dp(float v) {
            return v * density;
        }

        enum GamePhase {
            TITLE,
            CHARACTER_SELECT,
            TABLE,
            REWARD,
            SHOP,
            RUN_OVER,
            VICTORY
        }

        enum BotPersonality {
            PLAYER,
            HOARDER,
            BULLY,
            CLEANER,
            IDIOT_GENIUS
        }

        enum CharacterClass {
            CARD_SHARK("Card Shark", "Once per table, reveal one random enemy card."),
            OLD_MASTER("Old Master", "First successful defense each table restores +3 Reputation."),
            MARKET_HUSTLER("Market Hustler", "+25% coins from wins; market prices reduced."),
            LUCKY_FOOL("Lucky Fool", "Start -10 Reputation; first lethal hit leaves you at 1.");

            final String title;
            final String effect;

            CharacterClass(String title, String effect) {
                this.title = title;
                this.effect = effect;
            }
        }

        enum TableModifier {
            NORMAL("Normal Table"),
            NO_MERCY("No Mercy Rule"),
            GREASY_CARDS("Greasy Cards"),
            FAMILY_TABLE("Family Table"),
            WEDDING_TABLE("Wedding Table"),
            BOSS_TABLE("Boss Table");

            final String label;

            TableModifier(String label) {
                this.label = label;
            }
        }

        enum BossType {
            GRANDMOTHER("The Grandmother"),
            WEDDING_TABLE_BOSS("The Wedding Table"),
            FINAL_FOOL("The Final Fool");

            final String title;

            BossType(String title) {
                this.title = title;
            }
        }

        enum Relic {
            BENT_ACE("Bent Ace", "First Ace played each table counts as trump."),
            GRANDMA_GLARE("Grandma's Glare", "Once per table, block the first enemy throw-in."),
            MARKED_DECK("Marked Deck", "Reveal next draw card on the HUD."),
            POCKET_SIX("Pocket Six", "Reduce Reputation damage once if you hold a 6."),
            BUS_STOP_LUCK("Bus Stop Luck", "Win with exactly 1 card left for bonus coins."),
            TRUMP_TATTOO("Trump Tattoo", "Lowest trump gets +1 effective defense rank."),
            BORROWED_COAT("Borrowed Coat", "Reduce all Reputation damage by 2."),
            VODKA_CONFIDENCE("Vodka Confidence", "First attack pressure grants +5 coins on success.");

            final String title;
            final String effect;

            Relic(String title, String effect) {
                this.title = title;
                this.effect = effect;
            }
        }

        enum Suit {
            CLUBS("♣", Color.parseColor("#1E1B18")),
            DIAMONDS("♦", Color.parseColor("#A12424")),
            HEARTS("♥", Color.parseColor("#B12929")),
            SPADES("♠", Color.parseColor("#1E1B18"));

            final String symbol;
            final int color;

            Suit(String symbol, int color) {
                this.symbol = symbol;
                this.color = color;
            }
        }

        static class Card {
            final Suit suit;
            final int rank;
            boolean forceTrumpThisTable;

            Card(Suit suit, int rank) {
                this.suit = suit;
                this.rank = rank;
            }

            int effectiveRank(GameState state, boolean forDefense) {
                int bonus = 0;
                if (forDefense && state.relics.contains(Relic.TRUMP_TATTOO)
                        && suit == state.trumpSuit
                        && rank <= 8) {
                    bonus = 1;
                }
                return rank + bonus;
            }

            String rankLabel() {
                if (rank <= 10) return String.valueOf(rank);
                if (rank == 11) return "J";
                if (rank == 12) return "Q";
                if (rank == 13) return "K";
                return "A";
            }

            String label() {
                return rankLabel() + suit.symbol;
            }
        }

        static class Deck {
            final List<Card> cards = new ArrayList<>();

            void fill36() {
                cards.clear();
                int[] ranks = {6, 7, 8, 9, 10, 11, 12, 13, 14};
                for (Suit s : Suit.values()) {
                    for (int r : ranks) cards.add(new Card(s, r));
                }
            }

            void shuffle(Random random) {
                Collections.shuffle(cards, random);
            }

            Card draw() {
                if (cards.isEmpty()) return null;
                return cards.remove(0);
            }

            Card peekTop() {
                if (cards.isEmpty()) return null;
                return cards.get(0);
            }

            Suit peekBottomSuit() {
                if (cards.isEmpty()) return Suit.HEARTS;
                return cards.get(cards.size() - 1).suit;
            }

            boolean isEmpty() {
                return cards.isEmpty();
            }

            int size() {
                return cards.size();
            }
        }

        static class Player {
            final String name;
            final boolean isBot;
            final BotPersonality personality;
            final List<Card> hand = new ArrayList<>();

            Player(String name, boolean isBot, BotPersonality personality) {
                this.name = name;
                this.isBot = isBot;
                this.personality = personality;
            }
        }

        static class GameState {
            GamePhase phase = GamePhase.TITLE;
            CharacterClass selectedClass = CharacterClass.CARD_SHARK;
            Deck deck = new Deck();
            Suit trumpSuit = Suit.HEARTS;
            List<Player> players = new ArrayList<>();
            final List<Relic> relics = new ArrayList<>();
            final List<Relic> offeredRelics = new ArrayList<>();
            TableModifier currentModifier = TableModifier.NORMAL;
            BossType currentBoss;
            int reputation;
            int coins;
            int round;
            int discardCount;
            boolean luckyFoolSaved;
            Card nextDrawPreview;
            String logMessage = "";
        }

        static class ButtonHitbox {
            final RectF rect;
            final Runnable action;

            ButtonHitbox(RectF rect, Runnable action) {
                this.rect = rect;
                this.action = action;
            }
        }

        static class FloatingText {
            final String text;
            final float x;
            float y;
            float life = 1f;
            final boolean positive;

            FloatingText(String text, float x, float y, boolean positive) {
                this.text = text;
                this.x = x;
                this.y = y;
                this.positive = positive;
            }
        }
    }
}
