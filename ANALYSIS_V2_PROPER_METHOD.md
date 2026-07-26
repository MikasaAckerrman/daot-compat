# 🔬 ПОЛНЫЙ АНАЛИЗ ПРОЕКТА (ПРАВИЛЬНЫЙ МЕТОД)

**Дата:** 26 июля 2026, 11:50 UTC
**Этап:** 1 - ИЗУЧЕНИЕ ПРОЕКТА БЕЗ ИЗМЕНЕНИЙ
**Правило:** Сначала понять, потом исправлять. Никакие изменения в код не вносятся.

---

## 📋 КАРТА ВСЕХ JAVA КЛАССОВ (31 файл)

### AOT Reflection (интеграция с Danny's AOT мода)
```
aot/
├─ AOTReflect.java           - Главный reflection API для крюков
├─ RemoteHookReflect.java    - Работа с удалёнными крюками
├─ SpearReflect.java         - Копьё (дополнительное оружие)
└─ Reflect.java              - Базовая reflection инфраструктура
```

### Input System (управление игроком)
```
input/
├─ KeybindEventListener.java      - Главный обработчик клавиш (вызывается каждый тик)
├─ GrappleKeybinds.java           - Определение клавиш (SPACE, SHIFT, W, S и т.д.)
├─ GrappleStateManager.java       - Отслеживание состояния игрока
├─ DoubleTapDetector.java         - Детекция двойного нажатия (для DEW)
├─ HotbarSwapHandler.java         - Переключение слотов в инвентаре
└─ KeybindRegistrationListener.java - Регистрация клавиш при загрузке игры
```

### Hook System (крюки)
```
hook/
├─ RemoteHookFollower.java    - Отслеживание положения крюков каждый тик
├─ HookTransformResolver.java - Синхронизация крюков с физическими объектами (Sable)
├─ DynamicHookData.java       - Данные привязки крюка к объекту
└─ DynamicHookMap.java        - Кэш состояния крюков
```

### Physics System (физика)
```
physics/
├─ DEWImpulseCalculator.java      - Расчёт импульса DEW (двойной тап SPACE)
├─ GrapplePhysicsController.java  - Главный контроллер физики (тик)
├─ RopeSegmentHandler.java        - Сегменты троса (огибание блоков)
├─ RopePhysicsIntegration.java    - Интеграция с физикой
└─ SableRopeIntegration.java      - Интеграция с физическими объектами Sable
```

### Sable Bridge (интеграция с Create: Aeronautics)
```
sable/
├─ SableBridge.java         - API для работы с физическими объектами
└─ SubLevelResolver.java    - Определение какой объект зацеплен (Vanilla vs Physics)
```

### Rendering System (отрисовка)
```
render/
├─ RopeLineRenderer.java     - Рисование линии троса
├─ RopeWrapParticles.java    - Частицы огибания
└─ SparkEffectRenderer.java  - Эффекты искр
```

### Gas System (ресурс для DEW)
```
gas/
└─ GasManager.java - Управление газом (регенерация, расход)
```

### Collision Detection
```
collision/
└─ HighSpeedSubLevelGuard.java - Защита от проникновения на быстром движущемся объекте
```

### Other
```
client/DAOTCompatKeyMappings.java - Регистрация keybinds
spear/ThunderSpearFollower.java   - Копьё Грома (доп. механика)
DAOTCompat.java                   - Главный модуль, вызывает всё
```

---

## 🔄 ПОЛНАЯ ЦЕПОЧКА СОБЫТИЙ КАЖДЫЙ ТИК

### ClientTickEvent.Post (LOWEST priority - после всех остальных)

Вызывается из `DAOTCompat.java`:

```
┌─ ClientTickEvent.Post (LOWEST) ────────────────┐
│                                                 │
│  1. RemoteHookFollower.tick()                  │
│     └─ Обновляет позиции крюков каждый тик    │
│        (синхронизирует с moving ships)          │
│                                                 │
│  2. HookTransformResolver.process()            │
│     ├─ Для каждого крюка:                      │
│     │  ├─ attach() - если первый раз           │
│     │  │  └─ Записывает локальную позицию     │
│     │  └─ follow() - каждый тик                │
│     │     └─ Трансформирует позицию крюка      │
│     └─ НА PHYSICS ОБЪЕКТАХ: важный шаг!        │
│                                                 │
│  3. GrapplePhysicsController.tick()            │
│     ├─ Обновляет длину троса (SPACE/SHIFT)    │
│     ├─ Применяет ограничение длины троса      │
│     ├─ Проверяет коллизии                     │
│     └─ Воспроизводит звуки                     │
│                                                 │
│  4. HighSpeedSubLevelGuard.tick()              │
│     └─ Защита от проникновения                │
│                                                 │
└─────────────────────────────────────────────────┘
```

### KeybindEventListener.onClientTickEnd() (END_CLIENT_TICK)

Вызывается из `DAOTCompat.java` в отдельном слушателе:

```
┌─ END_CLIENT_TICK ──────────────┐
│                                │
│  1. GrappleStateManager.update()│
│     └─ Обновляет состояние     │
│        (SPACE held? SHIFT held?)│
│                                │
│  2. GasManager.tick()          │
│     └─ Регенерирует газ        │
│                                │
│  3. DoubleTapDetector.detect*()│
│     ├─ detectDoubleTapSpace()  │
│     │  └─ Проверяет DEW (2x S) │
│     └─ detectDoubleTapS()      │
│        └─ Проверяет Reverse DEW│
│                                │
│  4. DEWImpulseCalculator       │
│     ├─ calculateDEW()          │
│     └─ calculateReverseDEW()   │
│        └─ Если активирован     │
│           добавляет импульс    │
│           к скорости           │
│                                │
│  5. Sound effects воспроизводят│
│                                │
└────────────────────────────────┘
```

### Одноразовые события

```
RegisterKeyMappingsEvent (при загрузке игры)
  ├─ KeybindRegistrationListener
  └─ Регистрирует все клавиши

ClientPlayerJoinedEvent (игрок зашёл)
  ├─ DoubleTapDetector.reset()
  ├─ GasManager.reset()
  └─ GrappleStateManager.reset()

ClientPlayerQuitEvent (игрок вышел)
  └─ Всё очищается
```

---

## 🏗️ АРХИТЕКТУРА И ПОТОКИ ДАННЫХ

```
INPUT層:
  Клавиши (SPACE, SHIFT, W, S, Mouse)
        ↓
  KeybindEventListener (каждый тик)
  GrappleStateManager (отслеживает состояние)
        ↓
        ├─→ DoubleTapDetector
        │   (проверяет 2x SPACE для DEW)
        │
        ├─→ GasManager
        │   (расходует газ для DEW)
        │
        └─→ DEWImpulseCalculator
            (добавляет импульс к скорости)


HOOK層:
  AOTReflect.getLeftHook() / getRightHook()
  (получает крюки из ODM мода)
        ↓
  RemoteHookFollower.tick()
  (обновляет позиции крюков)
        ↓
  HookTransformResolver.process()
  ├─ VANILLA БЛОКИ:
  │  ├─ attach() → сохраняет позицию
  │  └─ follow() → обновляет позицию
  │
  └─ PHYSICS ОБЪЕКТЫ (Sable):
     ├─ SubLevelResolver.findContaining()
     │  (определяет какой объект зацеплен)
     │
     └─ SableBridge.getSubLevel()
        (получает физический объект)
        │
        └─ transformPosition()
           (пересчитывает координаты)


ROPE層:
  currentRopeLength (переменная в контроллере)
        ↓
  GrapplePhysicsController.tick()
  ├─ Если SPACE: currentRopeLength -= REEL_SPEED
  ├─ Если SHIFT: currentRopeLength += RELEASE_SPEED
  └─ applyRopeConstraint()
     (применяет ограничение к игроку)


PHYSICS層:
  GrapplePhysicsController.tick()
  ├─ checkRopeCollision()
  │  (проверяет столкновение)
  │
  ├─ applyRopeConstraint()
  │  ├─ Получает позиции крюков
  │  ├─ Вычисляет расстояние до игрока
  │  ├─ Если расстояние > currentRopeLength:
  │  │  └─ НАТЯЖЕНИЕ (tension) - тянет игрока
  │  │
  │  └─ player.setDeltaMovement()
  │     (обновляет скорость игрока)
  │
  └─ Sound effects


RENDER層:
  RopeLineRenderer.tick()
  ├─ Рисует линию от игрока к крюку
  └─ RopeWrapParticles.tick()
     └─ Частицы если трос огибает


OUTPUT:
  Движение игрока
  Звук
  Визуальные эффекты
```

---

## 🔄 ГЛАВНОЕ РАЗЛИЧИЕ: VANILLA vs PHYSICS

### На обычных блоках (Vanilla)

```
HookTransformResolver.attach():
  ├─ SubLevelResolver.findContaining(world, hookPos)
  ├─ Вернёт NULL (нет физических объектов)
  └─ recoverPlotFrame() → тоже NULL
  
Результат: крюк привязан к простой позиции в мире
```

### На физических объектах (Sable)

```
HookTransformResolver.attach():
  ├─ SubLevelResolver.findContaining(world, hookPos)
  ├─ Вернёт SubLevel объект ✓
  └─ Сохраняет:
     ├─ UUID физического объекта
     ├─ Локальную позицию (в координатах объекта)
     └─ Размер (dimension)

Каждый тик - HookTransformResolver.follow():
  ├─ Получает SubLevel по UUID
  ├─ Трансформирует локальную позицию
  │  (через pose.transformPosition())
  ├─ Получается WORLD позиция крюка
  └─ Обновляет положение крюка в мире

ПРОБЛЕМА: Если transformPosition() вернёт NULL
           или SubLevel будет удалён
           → крюк "потеряется"
```

---

## ⚠️ НАЙДЕННЫЕ ТОЧКИ РАЗДЕЛЕНИЯ ЛОГИКИ

### 1. HookTransformResolver.attach()
**Строки ~66-83**

```java
SubLevel sl = SubLevelResolver.findContaining(level, world);
if (sl != null) {
    // PHYSICS PATH
    UUID id = sl.getUniqueId();
    Vec3 local = sl.logicalPose().transformPositionInverse(world);
    DynamicHookMap.put(hook, new DynamicHookData(id, local, level.dimension()));
} else {
    // VANILLA PATH (или плот Sable)
    recoverPlotFrame(level, hook, world);
}
```

**Здесь происходит разделение!**

### 2. HookTransformResolver.follow()
**Строки ~114-147**

```java
SubLevel sl = SableBridge.getSubLevel(level, anchor.subLevelId());
if (sl != null) {
    // PHYSICS PATH: синхронизация с движущимся объектом
    Vec3 next = sl.logicalPose().transformPosition(anchor.localPosition());
    AOTReflect.setPosition(hook, next);
} else {
    // VANILLA PATH: просто DROP (отпустить крюк)
    drop(hook);
}
```

**Именно здесь происходит синхронизация!**

### 3. SubLevelResolver.findContaining()
**Главное звено**

```
Это метод определяет:
- Какой крюк на обычном блоке
- Какой крюк на физическом объекте

НУЖНО ИЗУЧИТЬ этот метод подробно!
```

---

## ❓ ВОПРОСЫ ДЛЯ ДАЛЬНЕЙШЕГО АНАЛИЗА

### Вопрос 1: Как работает Left Hook спам?
- Вызывается ли `attach()` несколько раз?
- Или `follow()` вызывается с NULL?
- Или проблема в RemoteHookFollower?

### Вопрос 2: Почему разное поведение на Vanilla vs Physics?
- `attach()` работает правильно?
- Или `follow()` не синхронизирует?
- Или проблема в applyRopeConstraint()?

### Вопрос 3: Почему после отпускания Space игрок падает?
- applyRopeConstraint() не очищается?
- Или GrappleStateManager.isDescending() не работает?
- Или проблема в physics логике?

### Вопрос 4: Почему W не даёт ускорения?
- Где вообще W используется?
- В каком классе W даёт импульс?
- Какова формула расчёта импульса?

### Вопрос 5: Почему DEW бесконечный?
- calculateDEW() вычисляет правильно?
- Но скорость суммируется без cap?
- Нужен MAX_VELOCITY?

### Вопрос 6: Rope Collision отсутствует?
- RopeSegmentHandler пустой?
- Или вообще не вызывается?
- Нужно ли огибание или просто коллизия?

---

## 📊 ТАБЛИЦА КОМПОНЕНТОВ

| Компонент | Файл | Роль | Вызывается |
|-----------|------|------|-----------|
| Input | KeybindEventListener.java | Читает клавиши | Каждый тик |
| Hook Tracking | RemoteHookFollower.java | Обновляет позиции | Каждый тик |
| Hook Sync | HookTransformResolver.java | Синхронизирует с Sable | Каждый тик |
| Physics | GrapplePhysicsController.java | Применяет ограничения | Каждый тик |
| DEW | DoubleTapDetector.java | Детектирует 2x SPACE | Каждый тик |
| DEW Impulse | DEWImpulseCalculator.java | Считает импульс | Когда DEW |
| Rope Segments | RopeSegmentHandler.java | Огибание блоков | ??? |
| Rendering | RopeLineRenderer.java | Рисует трос | Каждый frame |

---

## ✅ СЛЕДУЮЩИЙ ШАГ

После этого анализа нужно:

1. **Прочитать подробно** каждый ключевой класс
2. **Понять как работает**:
   - RemoteHookFollower (почему left hook спамит?)
   - HookTransformResolver (почему physics рассинхронизируется?)
   - GrapplePhysicsController (почему разное поведение?)
   - DEWImpulseCalculator (почему бесконечный импульс?)
3. **Найти корневые причины**
4. **Только потом начать исправления**

---

**Статус:** АНАЛИЗ ПРОЕКТА СТРУКТУРЫ ЗАВЕРШЁН
**Следующее:** Глубокий анализ ключевых классов (БЕЗ ИЗМЕНЕНИЙ)

