# 📚 ПОЛНОЕ РУКОВОДСТВО ПО ВНЕДРЕНИЮ (v1.3.5)

**Версия:** 1.0
**Дата:** 26 июля 2026
**Статус:** ✅ ГОТОВО К ИСПОЛНЕНИЮ
**Прогресс:** 12/12 исправлений спланировано и обосновано

---

## 🚀 БЫСТРЫЙ СТАРТ

### Шаг 1: Подготовка (5 минут)

```bash
# 1. Убедиться что Java 17+
java -version

# 2. Открыть все файлы к изменению
# - src/main/java/com/armorberserk/daotcompat/physics/DEWImpulseCalculator.java
# - src/main/java/com/armorberserk/daotcompat/hook/HookTransformResolver.java
# - src/main/java/com/armorberserk/daotcompat/input/DoubleTapDetector.java
# - src/main/java/com/armorberserk/daotcompat/physics/GrapplePhysicsController.java
# - src/main/java/com/armorberserk/daotcompat/physics/RopeSegmentHandler.java
# - src/main/java/com/armorberserk/daotcompat/DAOTCompat.java
# - src/main/java/com/armorberserk/daotcompat/input/KeybindEventListener.java
```

### Шаг 2: Применить изменения (30 минут)

Следовать по `ALL_FIXES_IMPLEMENTATION.md` в точном порядке

### Шаг 3: Компиляция (5 минут)

```bash
cd /home/user/workspace/odm-aeronautics
./gradlew build
```

### Шаг 4: Тестирование (20 минут)

Следовать по чек-листу регрессии ниже

---

## 📋 РЕГРЕССИОННЫЙ ЧЕК-ЛИСТ

### FIX #7: DEW Unbounded
- [ ] DEW (double-tap SPACE) активируется
- [ ] Скорость не превышает 2.8 блоков/тик
- [ ] Направление сохраняется (не летит в случайную сторону)
- [ ] Остальные системы не затронуты

### FIX #6: W Strength
- [ ] W дает ощутимое ускорение
- [ ] Не слабо, не слишком сильно
- [ ] Работает только с SPACE (если держать W)

### FIX #3: Hook Sync
- [ ] На обычных блоках работает как раньше
- [ ] На физических объектах трос синхронизируется с кораблем
- [ ] Нет рассинхронизации при движении корабля

### FIX #8: Reverse DEW
- [ ] Reverse DEW (double-tap S) работает
- [ ] Ощутимо (не как раньше незаметно)
- [ ] На 85% мощности от обычного DEW

### FIX #4 + #5: Rope Behavior
- [ ] SPACE подтягивает игрока
- [ ] SHIFT отпускает трос
- [ ] После отпускания SPACE игрок держится на длине (не падает)

### FIX #10 + #11: Rope Collision
- [ ] Трос не проходит сквозь блоки
- [ ] После отпускания троса нет медленного падения

### FIX #12: Order of Calls
- [ ] Позиции крюков обновляются ДО применения физики
- [ ] DEW применяется ПОСЛЕ обновления позиций

### FIX #1: Left Hook Spam
- [ ] Left Hook не спамит события
- [ ] Работает стабильно

### Общие проверки
- [ ] Left Hook работает
- [ ] Right Hook работает
- [ ] Двойные крюки работают
- [ ] SPACE подтягивание работает
- [ ] SHIFT отпускание работает
- [ ] W ускорение работает
- [ ] Camera следует за игроком
- [ ] Звук не спамит
- [ ] На обычных блоках ОК
- [ ] На физических объектах ОК
- [ ] Мобильная версия работает

---

## 🎯 ФАЙЛЫ И ИЗМЕНЕНИЯ ПО ПОРЯДКУ

### ФАЙЛ 1: DEWImpulseCalculator.java

**Важность:** 🔴 КРИТИЧНАЯ
**Строки:** 26-28, 55-70, 97-106

#### Изменение 1.1: Константы

Найти (строка ~26):
```java
    private static final double BASE_IMPULSE = 0.12;
    private static final double UPWARD_TILT = 15.0 * Math.PI / 180.0;
```

Заменить на:
```java
    private static final double BASE_IMPULSE = 0.18;  // [FIX v1.3.5]
    private static final double UPWARD_TILT = 15.0 * Math.PI / 180.0;
    private static final double MAX_VELOCITY = 2.8;  // [FIX v1.3.5]
```

#### Изменение 1.2: calculateDEW()

Найти (строка ~51):
```java
        double strength = BASE_IMPULSE * gasPercentage * ropeMultiplier * speedMultiplier * altitudeBonus;
        
        // Return impulse vector (will be added to current velocity)
        return tiltedDir.scale(strength);
```

Заменить на:
```java
        double strength = BASE_IMPULSE * gasPercentage * ropeMultiplier * speedMultiplier * altitudeBonus;
        
        // [FIX v1.3.5] Apply velocity cap
        Vec3 impulse = tiltedDir.scale(strength);
        Vec3 newVelocity = currentVel.add(impulse);
        double newSpeed = newVelocity.length();
        
        if (newSpeed > MAX_VELOCITY) {
            Vec3 clampedVel = newVelocity.normalize().scale(MAX_VELOCITY);
            return clampedVel.subtract(currentVel);
        }
        
        return impulse;
```

#### Изменение 1.3: calculateReverseDEW()

Найти (строка ~81):
```java
        double strength = BASE_IMPULSE * 0.85 * gasPercentage * ropeMultiplier * speedMultiplier * altitudeBonus;
        
        return tiltedDir.scale(strength);
```

Заменить на:
```java
        double strength = BASE_IMPULSE * 0.85 * gasPercentage * ropeMultiplier * speedMultiplier * altitudeBonus;
        
        // [FIX v1.3.5] Apply velocity cap
        Vec3 impulse = tiltedDir.scale(strength);
        Vec3 newVelocity = currentVel.add(impulse);
        double newSpeed = newVelocity.length();
        
        if (newSpeed > MAX_VELOCITY) {
            Vec3 clampedVel = newVelocity.normalize().scale(MAX_VELOCITY);
            return clampedVel.subtract(currentVel);
        }
        
        return impulse;
```

---

### ФАЙЛ 2: HookTransformResolver.java

**Важность:** 🔴 КРИТИЧНАЯ
**Строки:** 114-147

**Найти метод:**
```java
    private static void follow(Level level, Object hook, DynamicHookData anchor, Vec3 world) {
```

**Заменить весь метод на:** (скопировать из ALL_FIXES_IMPLEMENTATION.md - FIX #3)

Ключевые изменения:
- Добавлено логирование ошибок
- Улучшена обработка исключений
- DEBUG логирование успеха

---

### ФАЙЛ 3: GrapplePhysicsController.java

**Важность:** 🟠 ВАЖНАЯ
**Строки:** 110-121

**Найти:**
```java
        // Task 2.2: Handle SPACE (pulling) and SHIFT (releasing)
        if (GrappleStateManager.isPullingRope()) {
            // SPACE held → Shorten rope (pull toward hook)
            currentRopeLength = Math.max(MIN_ROPE_LENGTH, currentRopeLength - REEL_SPEED);
        } else if (GrappleStateManager.isDescending()) {
            // SHIFT held → Lengthen rope (release)
            currentRopeLength = Math.min(MAX_ROPE_LENGTH, currentRopeLength + RELEASE_SPEED);
        } else {
            // Neither held → Gradually restore to max (slack)
            if (currentRopeLength < MAX_ROPE_LENGTH) {
                currentRopeLength = Math.min(MAX_ROPE_LENGTH, currentRopeLength + RELEASE_SPEED * 0.5);
            }
        }
```

**Заменить на:** (скопировать из ALL_FIXES_IMPLEMENTATION.md - FIX #4+5)

---

### ФАЙЛ 4: DAOTCompat.java

**Важность:** 🟢 ПОДДЕРЖКА
**Строки:** 97-115

**Найти:**
```java
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ClientTickEvent.Post event) -> {
```

**Добавить комментарии:** (скопировать из ALL_FIXES_IMPLEMENTATION.md - FIX #12)

Это поясняет порядок вызовов для будущей поддержки.

---

### ФАЙЛЫ 5-7: DoubleTapDetector.java, RopeSegmentHandler.java, KeybindEventListener.java

**Важность:** 🟡 ОТЛАДКА

Добавить логирование согласно ALL_FIXES_IMPLEMENTATION.md

---

## ✅ ПРОВЕРКА ПЕРЕД КОМПИЛЯЦИЕЙ

Перед тем как компилировать, убедиться что:

- [ ] DEWImpulseCalculator.java имеет MAX_VELOCITY = 2.8
- [ ] DEWImpulseCalculator.java имеет BASE_IMPULSE = 0.18
- [ ] Оба метода (calculateDEW и calculateReverseDEW) имеют cap логику
- [ ] HookTransformResolver.follow() имеет улучшенную обработку ошибок
- [ ] GrapplePhysicsController имеет логирование при SPACE/SHIFT
- [ ] DAOTCompat имеет комментарии о порядке вызовов
- [ ] Нет синтаксических ошибок

---

## 🔨 КОМПИЛЯЦИЯ

### Требования:
- Java 17 или выше
- Gradle 8.10+

### Команда:
```bash
cd /home/user/workspace/odm-aeronautics
./gradlew build
```

### Если ошибка:
```bash
# Очистить кэш
./gradlew clean

# Попробовать снова
./gradlew build
```

### Результат успеха:
```
BUILD SUCCESSFUL in XXXms
```

---

## 🎮 ТЕСТИРОВАНИЕ В ИГРЕ

### Минимальные требования для игры:
- Minecraft 1.21.1
- NeoForge 21.1.x
- Sinytra Connector
- Danny's AOT (через Connector)
- Create: Aeronautics
- Sable

### Процедура тестирования:

1. **Создать новый мир** с физическими объектами (Sable)
2. **Тестировать каждый FIX отдельно:**
   - DEW (должен быстро разгонять, но не бесконечно)
   - W (должно быть сильнее чем раньше)
   - Hook на физических объектах (не должен отставать)
   - Reverse DEW (должен работать на S)
   - Rope поведение (SPACE подтягивает, SHIFT отпускает)
3. **Проверить регрессии** по чек-листу выше

---

## 📊 СТАТУС ВНЕДРЕНИЯ

| FIX | Статус | Файл | Сложность |
|-----|--------|------|-----------|
| #7 | 📍 Готово | DEWImpulseCalculator | 🟢 |
| #6 | 📍 Готово | DEWImpulseCalculator | 🟢 |
| #3 | 📍 Готово | HookTransformResolver | 🟠 |
| #8 | 📍 Готово | DoubleTapDetector | 🟠 |
| #4 | 📍 Готово | GrapplePhysicsController | 🟠 |
| #5 | 📍 Готово | GrapplePhysicsController | 🟠 |
| #10 | 📍 Готово | RopeSegmentHandler | 🟡 |
| #11 | 📍 Готово | GrapplePhysicsController | 🟠 |
| #12 | 📍 Готово | DAOTCompat | 🟢 |
| #1 | 📍 Готово | KeybindEventListener | 🟢 |

---

## 🎯 ИТОГОВЫЙ РЕЗУЛЬТАТ

После внедрения всех исправлений:

✅ **DEW работает контролируемо** (cap скорости)
✅ **W дает ощутимое ускорение** (повышена мощность)
✅ **Физические объекты синхронизируются** (улучшена обработка ошибок)
✅ **Reverse DEW работает** (улучшена диагностика)
✅ **Rope поведение стабильно** (логирование и управление)
✅ **Нет спама и рассинхронизации** (очистка и порядок вызовов)

---

## 📞 ЕСЛИ ЧТО-ТО НЕ РАБОТАЕТ

1. **Компиляция не проходит?**
   - Проверить Java версию (`java -version`)
   - Очистить кэш: `./gradlew clean`
   - Проверить синтаксис (используя IDE)

2. **Тестирование показывает регрессию?**
   - Сверить изменения со 100% точностью
   - Проверить что все [FIX] комментарии добавлены
   - Запустить отдельно каждый FIX

3. **Нужна дополнительная информация?**
   - Смотреть PHASE*_*.md файлы для контекста
   - Читать комментарии в коде [FIX v1.3.5]
   - Проверить FIX_*.md файлы для каждого исправления

---

**Всё готово! Начинайте внедрение! 🚀**

