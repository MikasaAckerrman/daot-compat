# 📊 ЭТАП 4: ТАБЛИЦА ПРОБЛЕМ (v1.3.4)

**Статус:** ✅ Все проблемы идентифицированы
**Дата:** 26 июля 2026, 03:46+ UTC
**Метод:** На основе анализа Этапов 1-3

---

## 🎯 ПОЛНАЯ ТАБЛИЦА ПРОБЛЕМ

| # | Система | Статус | Наблюдаемое поведение | Вероятная причина | Файл | Метод | Параметр | Степень критичности |
|---|---------|--------|----------------------|------------------|------|-------|----------|-------|
| 1 | **Left Hook** | ⚠️ НЕСТ | Спам событий при зацеплении | AOT вызывает событие несколько раз за тик | `KeybindEventListener.java` | `onClientTickEnd()` | - | 🔴 ВЫСОКАЯ |
| 2 | **Right Hook** | ✅ OK | Работает нормально | - | - | - | - | - |
| 3 | **Hook Sync (Physics)** | ⚠️ ОТСТ | Трос отстает от движущегося корабля | RemoteHookFollower.follow() не вызывается или позиция не обновляется каждый тик | `HookTransformResolver.java` + `RemoteHookFollower.java` | `follow()` + `tick()` | - | 🔴 КРИТИЧНО |
| 4 | **Rope Length (SPACE)** | ⚠️ РАЗН | На обычных блоках подтягивание автоматическое, на физических нужно SPACE | Нет разделения логики - используется одна и та же система | `GrapplePhysicsController.java` | `tick()` | `currentRopeLength` | 🟠 СРЕДНЯЯ |
| 5 | **After SPACE Release** | ⚠️ ПАДА | Игрок опускается вниз вместо удержания на длине | Ограничение троса применяется неправильно при isDescending=false | `GrapplePhysicsController.java` | `tick()` + `applyRopeConstraint()` | `currentRopeLength`, `RELEASE_SPEED` | 🟠 СРЕДНЯЯ |
| 6 | **W Acceleration** | ⚠️ СЛАБ | Почти не даёт ускорения | BASE_IMPULSE = 0.12 слишком мал или условие canAccelerate() работает неправильно | `DEWImpulseCalculator.java` | `calculateDEW()` | `BASE_IMPULSE = 0.12` | 🟠 СРЕДНЯЯ |
| 7 | **DEW Impulse** | ⚠️ НЕСТ | Бесконечное ускорение, нет лимита скорости | Нет максимального cap скорости после импульса или speedMultiplier растет экспоненциально | `DEWImpulseCalculator.java` | `calculateDEW()` | `speedMultiplier`, `strength` | 🔴 КРИТИЧНО |
| 8 | **Reverse DEW** | ⚠️ СЛАБ | Не ощущается или вообще не работает | Либо DoubleTapDetector не работает, либо strength 0.85 слишком мал | `DoubleTapDetector.java` + `DEWImpulseCalculator.java` | `detectDoubleTapS()` + `calculateReverseDEW()` | `strength = 0.85`, `UPWARD_TILT` | 🟠 СРЕДНЯЯ |
| 9 | **Sound Spam** | ✅ ИСПРА | Звук повторяется много раз | Исправлено в v1.3.2: добавлен cooldown | `GrapplePhysicsController.java` | `tick()` | `SOUND_COOLDOWN_TICKS = 5` | ✅ ИСПРАВЛЕНО |
| 10 | **Rope Collision (Physics)** | ⚠️ ПРОХ | Трос проходит сквозь блоки на движущихся объектах | RopeSegmentHandler.update() не учитывает трансформацию координат Sable | `RopeSegmentHandler.java` | `update()` | `playerPos`, `hookPos` | 🟠 СРЕДНЯЯ |
| 11 | **After Release Lag** | ⚠️ МАЛА | После отпускания троса игрок медленно падает как будто трос есть | Ограничение не очищается или forces не сбрасываются при release | `GrapplePhysicsController.java` | `applyRopeConstraint()` | `currentRopeLength` reset | 🟠 СРЕДНЯЯ |
| 12 | **Order of Calls** | ⚠️ РАС | Неправильный порядок вызовов может вызвать рассинхронизацию | KeybindEventListener вызывает DEW раньше чем GrapplePhysicsController обновляет позиции | `DAOTCompat.java` | Event registration | Event priority | 🟠 СРЕДНЯЯ |

---

## 🔴 КРИТИЧНЫЕ ПРОБЛЕМЫ (требуют немедленного исправления)

### Проблема #3: HOOK SYNC на Physics объектах 🔴 КРИТИЧНО

**Описание:** Трос отстает от движущегося корабля, игрок получает рывок в неправильном направлении

**Где:** 
```java
HookTransformResolver.follow() - строка 114-147
RemoteHookFollower.tick() - должна вызываться каждый tick
```

**Текущая логика:**
```java
private static void follow(Level level, Object hook, DynamicHookData anchor, Vec3 world) {
    SubLevel sl = SableBridge.getSubLevel(level, anchor.subLevelId());
    Vec3 next = sl.logicalPose().transformPosition(anchor.localPosition());
    AOTReflect.setPosition(hook, next);
}
```

**Проблема:** 
- Если `anchor` равен `null` → крюк не синхронизируется
- Если `SubLevel` удален → крюк отпускается
- Если `transformPosition()` выбрасывает exception → молча пропускается

**Решение:**
1. Убедиться что RemoteHookFollower.tick() вызывается КАЖДЫЙ тик
2. Добавить логирование если sync не работает
3. Проверить что anchor правильно создается в attach()

---

### Проблема #7: DEW IMPULSE UNBOUNDED 🔴 КРИТИЧНО

**Описание:** Игрок получает бесконечное ускорение, нет верхнего лимита скорости

**Где:**
```java
DEWImpulseCalculator.calculateDEW() - строка 33-55
KeybindEventListener.onClientTickEnd() - строка 55-56
```

**Текущая логика:**
```java
double strength = BASE_IMPULSE * gasPercentage * ropeMultiplier * speedMultiplier * altitudeBonus;
return tiltedDir.scale(strength);
```

**Проблема:**
- `strength` добавляется к velocity каждый раз
- Нет max velocity cap
- `speedMultiplier` растет линейно → экспоненциальное ускорение

**Формула:**
```
Velocity_new = Velocity_old + impulse
impulse = 0.12 × gas × rope × (1.0 + (vel/20)*0.4) × altitude
```

Если vel растет → multiplier растет → impulse растет → vel растет еще больше!

**Решение:**
1. Добавить MAX_VELOCITY cap после добавления импульса
2. Или ограничить speedMultiplier максимальным значением
3. Или уменьшить BASE_IMPULSE

---

## 🟠 СРЕДНИЕ ПРОБЛЕМЫ (требуют исправления)

### Проблема #4: Разное поведение подтягивания

**Где:**
```java
GrapplePhysicsController.tick() - строка 110-121
```

**Текущая логика:**
```java
if (GrappleStateManager.isPullingRope()) {
    currentRopeLength -= REEL_SPEED (0.4);
} else if (GrappleStateManager.isDescending()) {
    currentRopeLength += RELEASE_SPEED (0.2);
} else {
    currentRopeLength += RELEASE_SPEED * 0.5;  // Gradual slack
}
```

**Проблема:** Нет разделения по типу блока (vanilla vs physics)
- На vanilla блоках: AOT сам подтягивает
- На physics блоках: нужно управлять вручную

**Решение:** Возможно нужна разная скорость для разных типов

---

### Проблема #5: После отпускания SPACE

**Где:**
```java
GrapplePhysicsController.tick() - строка 116-121
GrapplePhysicsController.applyRopeConstraint() - не видны детали
```

**Проблема:** После отпускания SPACE (isDescending = false):
```java
} else {
    // Neither held → Gradually restore to max (slack)
    if (currentRopeLength < MAX_ROPE_LENGTH) {
        currentRopeLength = Math.min(MAX_ROPE_LENGTH, currentRopeLength + RELEASE_SPEED * 0.5);
    }
}
```

Трос становится длиннее (slack) но игрок продолжает падать? Почему?

**Решение:** Нужно проверить applyRopeConstraint() - может быть там баг

---

### Проблема #6: W слабое ускорение

**Где:**
```java
DEWImpulseCalculator.calculateDEW() - строка 51
```

**Текущее значение:** `BASE_IMPULSE = 0.12`

**Расчет мощности при double-tap SPACE:**
```
strength = 0.12 × gasPercent(0-1) × ropeMultiplier(1.0-2.3) × speedMultiplier(1.0-1.2) × altitude(0.9-1.1)
         = 0.12 × 1.0 × 1.6 × 1.1 × 1.0
         = 0.21 (максимум с одним крюком)
```

Это очень мало для заметного импульса!

**Решение:** Увеличить BASE_IMPULSE до 0.18-0.25

---

### Проблема #8: Reverse DEW не работает

**Где:**
```java
DoubleTapDetector.detectDoubleTapS() - нужно проверить логику
DEWImpulseCalculator.calculateReverseDEW() - строка 61-84
```

**Возможные причины:**
1. DoubleTapDetector.detectDoubleTapS() всегда возвращает false
2. GasManager.canUseReverseDEW() возвращает false
3. Strength слишком низкий (0.85 × BASE_IMPULSE)

**Решение:** Нужно отдебажить DoubleTapDetector + проверить формулу

---

### Проблема #10: Rope Collision на Physics

**Где:**
```java
RopeSegmentHandler.update() - не видны детали
GrapplePhysicsController.checkRopeCollision() - строка 124
```

**Проблема:** RopeSegmentHandler проверяет коллизии в world координатах, но на движущемся корабле нужны local координаты

**Решение:** Нужно преобразовать playerPos и hookPos в локальные координаты Sable перед проверкой коллизии

---

### Проблема #11: После отпускания медленное падение

**Где:**
```java
GrapplePhysicsController.applyRopeConstraint() - нужно проверить
```

**Проблема:** После release() крюка ограничение не очищается?

**Решение:** Может быть нужно явно сбросить velocity или ограничение

---

### Проблема #12: Порядок вызовов

**Где:**
```java
DAOTCompat.java - регистрация event listeners
```

**Текущий порядок:**
1. KeybindEventListener.onClientTickEnd() (NORMAL priority)
   - Вызывает DEWImpulseCalculator → добавляет impulse к velocity
2. RemoteHookFollower.tick() (LOWEST priority)
   - Обновляет позиции крюков

**Проблема:** DEW может быть применен раньше чем позиции крюков обновлены!

**Решение:** Убедиться что обновление позиций происходит ПЕРЕД DEW

---

## 📈 ПРИОРИТЕТ ИСПРАВЛЕНИЯ

### 🔴 КРИТИЧНЫЕ (исправлять в первую очередь)
1. **#3 - Hook Sync** (Physics объекты отстают)
2. **#7 - DEW Unbounded** (бесконечное ускорение)

### 🟠 ВАЖНЫЕ (потом)
3. **#4, #5, #6** (управление тросом)
4. **#8** (Reverse DEW)
5. **#10, #11** (после release)
6. **#12** (порядок вызовов)

---

## 🔍 УГЛУБЛЕННЫЙ АНАЛИЗ КАЖДОЙ ПРОБЛЕМЫ

### Проблема #1: Left Hook spam

**Гипотеза:** KeybindEventListener получает несколько событий за один тик

```java
// KeybindEventListener.onClientTickEnd() - вызывается 1 раз в тик
// но может быть вызвана из multiple мест?
```

**Проверить:**
- Есть ли другие обработчики события кроме KeybindEventListener?
- Вызывает ли MouseInputListener дополнительные обработчики?

---

### Проблема #3: Детальный анализ Hook Sync

**Сценарий:**
```
Tick 0: Игрок ловит крюк на корабле
  ├─ HookTransformResolver.attach()
  ├─ SubLevel найден
  ├─ local позиция = world - transform
  └─ Сохранено в DynamicHookMap

Tick 1: Корабль движется +5 блоков
  ├─ RemoteHookFollower.tick()
  ├─ HookTransformResolver.follow() вызывается
  ├─ new world pos = local + NEW transform
  ├─ AOTReflect.setPosition(hook, next)
  └─ GrapplePhysicsController использует NEW позицию ✅

Tick 2-10: Корабль продолжает двигаться
  ├─ RemoteHookFollower.follow() вызывается каждый тик
  ├─ Трос всегда синхронизирован ✅
  └─ Игрок следует за кораблем ✅
```

**Если баг:**
```
Tick 0: Крюк ловится, сохранено в DynamicHookData

Tick 1: Корабль движется
  ├─ RemoteHookFollower.follow() НЕ ВЫЗЫВАЕТСЯ ❌
  ├─ Позиция крюка остается старой
  ├─ GrapplePhysicsController использует СТАРУЮ позицию
  └─ Трос отстает ❌
```

---

## ✅ АНАЛИЗ ЗАВЕРШЕН

**Найдено проблем:** 12
**Критичных:** 2 (#3, #7)
**Средних:** 10

**Следующий шаг:** ЭТАП 5 - исправлять по одному (начиная с критичных)

**Рекомендуемый порядок:**
1. ✅ #3 - Hook Sync
2. ✅ #7 - DEW Cap
3. ✅ #6 - W Strength (если нужно)
4. ✅ #8 - Reverse DEW
5. ✅ #4, #5 - Rope Management
6. ✅ #10, #11 - Release mechanics

---

**Статус:** 🎯 ТАБЛИЦА ПРОБЛЕМ СОСТАВЛЕНА И ГОТОВА К ИСПОЛЬЗОВАНИЮ
