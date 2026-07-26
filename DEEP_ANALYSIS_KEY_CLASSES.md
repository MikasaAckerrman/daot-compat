# 🔬 ГЛУБОКИЙ АНАЛИЗ КЛЮЧЕВЫХ КЛАССОВ

**Дата:** 26 июля 2026
**Этап:** 1.2 - ПОНИМАНИЕ ПРОБЛЕМ БЕЗ ИЗМЕНЕНИЙ

---

## 🎯 ВОПРОС 1: Почему Left Hook спамит?

### Гипотеза
Left Hook постоянно пытается зацепиться (спам события).

### Где это может быть
1. **RemoteHookFollower.java** - для других игроков (не наш случай)
2. **HookTransformResolver.java** - для локального игрока (НАШ СЛУЧАЙ)
3. **KeybindEventListener.java** - может быть вызов повторяется

### Изучу HookTransformResolver.follow() (строки 114-147)

```java
private static void follow(Level level, Object hook, DynamicHookData anchor, Vec3 world) {
    // Guard against a stale anchor from a previous dimension...
    if (!anchor.dimensionKey().equals(level.dimension())) {
        DAOTCompat.LOGGER.debug("[hook] dropped anchor: dimension changed...");
        drop(hook);
        return;
    }
    SubLevel sl = SableBridge.getSubLevel(level, anchor.subLevelId());
    if (sl == null) {
        drop(hook);  // ← ЕСЛИ NULL, вызывает drop()
        return;
    }
    Vec3 next;
    try {
        next = sl.logicalPose().transformPosition(anchor.localPosition());
    } catch (Throwable t) {
        drop(hook);  // ← ЕСЛИ ОШИБКА, вызывает drop()
        return;
    }
    if (notFinite(next)) {
        drop(hook);  // ← ЕСЛИ НЕ КОНЕЧНАЯ, вызывает drop()
        return;
    }
    if (world.distanceToSqr(next) < IDLE_SQR) return;
    
    LocalPlayer player = Minecraft.getInstance().player;
    if (player != null && next.distanceToSqr(player.position()) > MATCH_RADIUS_SQR) {
        drop(hook);  // ← ЕСЛИ ДАЛЕКО, вызывает drop()
        return;
    }
    AOTReflect.setPosition(hook, next);  // ← ОБНОВЛЯЕТ ПОЗИЦИЮ
}
```

**ПРОБЛЕМА:** Если `SableBridge.getSubLevel()` часто возвращает NULL,
то `drop(hook)` вызывается каждый тик!

### Но где спам СОБЫТИЯ?

Спам события - это когда крюк **выпускается и переподключается** много раз.

**Гипотеза:** 
1. Крюк на Sable объекте
2. `getSubLevel()` иногда возвращает NULL
3. `drop(hook)` отпускает крюк
4. Следующий тик крюк снова активируется
5. `attach()` переподключает его
6. Повторяется... спам!

### Проверка: RemoteHookReflect vs AOTReflect

`drop()` вызывает `AOTReflect.release(hook)`.

Это **ВСЕ** отпускает или только синхронизирует?

---

## 🎯 ВОПРОС 2: Почему разное поведение на Vanilla vs Physics?

### Наблюдение
- **Vanilla блоки:** игрок автоматически начинает подтягиваться
- **Physics объекты:** нужно держать SPACE для подтягивания

### Поиск кода

`applyRopeConstraint()` в GrapplePhysicsController.java (строки 164+)

Это метод применяет ограничение длины троса к игроку.

**ВОПРОС:** Вызывается ли `applyRopeConstraint()` одинаково для обоих случаев?

**ВЕРОЯТНАЯ ПРИЧИНА:**
- На Vanilla: HookTransformResolver правильно обновляет позицию крюка
- На Physics: `getSubLevel()` возвращает NULL → `drop()` → крюк отпускается → нужно переподключать SPACE

---

## 🎯 ВОПРОС 3: Почему после отпускания Space игрок падает?

### Наблюдение
После отпускания SPACE игрок начинает медленно опускаться.

**Должно быть:** остаться на текущей длине троса (держать позицию).

### Где код управляет SPACE?

GrapplePhysicsController.java (строки 109-121):

```java
if (GrappleStateManager.isPullingRope()) {
    currentRopeLength = Math.max(MIN_ROPE_LENGTH, currentRopeLength - REEL_SPEED);
} else if (GrappleStateManager.isDescending()) {
    currentRopeLength = Math.min(MAX_ROPE_LENGTH, currentRopeLength + RELEASE_SPEED);
} else {
    // Neither held → Gradually restore to max (slack)
    if (currentRopeLength < MAX_ROPE_LENGTH) {
        currentRopeLength = Math.min(MAX_ROPE_LENGTH, currentRopeLength + RELEASE_SPEED * 0.5);
    }
}
```

**ПРОБЛЕМА:** Когда ни SPACE ни SHIFT не удерживаются,
трос **медленно возвращается к MAX_ROPE_LENGTH**.

Это вызывает медленное падение!

**ШАГ 1:** Игрок подтянулся (currentRopeLength = 5)
**ШАГ 2:** Отпустил SPACE
**ШАГ 3:** Трос начинает удлиняться (5 → 6 → 7 → 8...)
**ШАГ 4:** Игрок опускается

**ПРИЧИНА:** Логика возврата к MAX_ROPE_LENGTH неправильна!

---

## 🎯 ВОПРОС 4: Почему W почти не даёт ускорения?

### Где W используется?

Нужно найти где W добавляет импульс...

**Гипотеза:** W может быть частью DEW или отдельного ускорения.

Поиск: "calculateDEW", "BASE_IMPULSE", "W"...

В DEWImpulseCalculator.java:

```java
double strength = BASE_IMPULSE * gasPercentage * ropeMultiplier * speedMultiplier * altitudeBonus;
```

**Параметры:**
- BASE_IMPULSE = 0.12 (базовый импульс)
- gasPercentage = 0-1 (процент газа)
- ropeMultiplier = 1.0, 1.6, или 2.3 (количество крюков)
- speedMultiplier = 1.0 + (скорость/20) * 0.4
- altitudeBonus = 0.9 или 1.1

**РАСЧЁТ при полном газе, 2 крюка, в воздухе:**
strength = 0.12 * 1.0 * 2.3 * 1.0 * 1.1 = 0.30

**Это ОЧЕНЬ мало!**

**ПРИЧИНА:** BASE_IMPULSE = 0.12 слишком малень!

---

## 🎯 ВОПРОС 5: Почему DEW бесконечный?

### Наблюдение
Игрок получает слишком большое ускорение, которое растёт бесконечно.

### Код DEWImpulseCalculator.calculateDEW()

```java
Vec3 impulse = tiltedDir.scale(strength);
// Return impulse vector (will be added to current velocity)
return impulse;
```

**КОД:**
1. Вычисляет импульс на основе параметров
2. Возвращает импульс
3. В KeybindEventListener:
   ```java
   Vec3 impulse = DEWImpulseCalculator.calculateDEW(player);
   player.setDeltaMovement(player.getDeltaMovement().add(impulse));  // ДОБАВЛЯЕТ!
   ```

**ПРОБЛЕМА:** 
- Нет верхнего предела скорости (MAX_VELOCITY)
- Если вызвать DEW дважды подряд, скорость будет x2 (или больше)
- Скорость растёт без ограничений

**ПРИЧИНА:** Отсутствует CAP скорости!

---

## 🎯 ВОПРОС 6: Rope Collision отсутствует?

### Наблюдение
Трос проходит сквозь блоки.

### RopeSegmentHandler.java

Класс существует, но является ли он ИСПОЛЬЗУЕМЫМ?

**Поиск:** Где вызывается RopeSegmentHandler?

В GrapplePhysicsController.java:

```java
private static final Map<Integer, RopeSegmentHandler> segmentHandlers = new HashMap<>();
```

Класс создаётся! Но...

**ВОПРОС:** Вызывается ли `segmentHandlers.update()`?

Поиск в `checkRopeCollision()` методе...

```java
private static void checkRopeCollision(LocalPlayer player, Object leftHook, Object rightHook) {
    // ... код ...
}
```

**ПРОБЛЕМА ПОТЕНЦИАЛЬНАЯ:**
1. RopeSegmentHandler существует
2. Но может быть **не вызывается** или **недоrealizован**

---

## 📊 ТАБЛИЦА НАЙДЕННЫХ ПРИЧИН (ПРЕДВАРИТЕЛЬНАЯ)

| # | Проблема | Класс | Метод | Вероятная причина |
|---|----------|-------|-------|-------------------|
| 1 | Left Hook спам | HookTransformResolver | follow() | getSubLevel() возвращает NULL → drop() каждый тик |
| 2 | Разное поведение | HookTransformResolver | follow() | На physics: getSubLevel() NULL → drop() → нужен SPACE |
| 3 | Падение после Space | GrapplePhysicsController | tick() | currentRopeLength восстанавливается к MAX → игрок опускается |
| 4 | W слабое | DEWImpulseCalculator | calculateDEW() | BASE_IMPULSE = 0.12 слишком мало |
| 5 | DEW бесконечный | DEWImpulseCalculator | calculateDEW() | Нет MAX_VELOCITY cap |
| 6 | Rope Collision | RopeSegmentHandler | ??? | Класс существует но не используется / не реализован |
| 7 | Reverse DEW слабый | DEWImpulseCalculator | calculateReverseDEW() | Слабый импульс |
| 8 | Звук спамит | GrapplePhysicsController | playRopeHookSound() | Вызывается каждый тик вместо один раз |

---

## ✅ СЛЕДУЮЩИЙ ШАГ

Нужно проверить:

1. **SableBridge.getSubLevel()** - почему возвращает NULL?
2. **RemoteHookFollower** vs **HookTransformResolver** - есть ли разница в логике?
3. **checkRopeCollision()** - реально ли вызывается и работает?
4. **RopeSegmentHandler** - полностью ли реализован?

**ГЛАВНОЕ:** Найти КОРНЕВУЮ ПРИЧИНУ, а не симптомы!

---

**Статус:** ГЛУБОКИЙ АНАЛИЗ КЛЮЧЕВЫХ КЛАССОВ ЗАВЕРШЁН

Я не вносил никаких изменений, только анализировал и выдвигал гипотезы.

