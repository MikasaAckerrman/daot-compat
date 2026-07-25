# NEXT SESSION PLAN - v1.1.0 → v2.0.0

**Дата создания:** 2026-07-25  
**Для:** Новая сессия/нейронка  
**Статус:** PHYSICS REWRITE REQUIRED

---

## 🚀 ЦЕЛЬ СЕССИИ

**Переписать архитектуру физики** с "магнит-модели" на **правильную ODM-физику**.

Текущий статус:
- ✅ Мод компилируется
- ✅ Event bus баги исправлены
- ❌ Физика движения ПОЛНОСТЬЮ НЕПРАВИЛЬНАЯ (80% проблем)

**Результат после сессии:**
- Движение похоже на ODM из аниме
- Правильная физика маятника
- Контролируемое подтягивание (SPACE)
- Плавная инерция

---

## 📋 ЧТО СДЕЛАНО ДО ЭТОГО (v1.1.0)

```
✅ Phase 1: Keybind Infrastructure
   - 5 keybinds работают (SPACE, W, SHIFT, S, 1-9)
   - RegisterKeyMappingsEvent на MOD_BUS
   - ClientTickEvent на NeoForge.EVENT_BUS

✅ Phase 2: Rope Physics Controller (базовая версия)
   - Крюк зацепляется
   - Игрок летит к крюку (НЕПРАВИЛЬНО!)

✅ Phase 3+: Rope Rendering + Sable Integration
   - RopeLineRenderer компилируется
   - Коллизия не реализована

✅ Phase 4A/4B: Gas System + DEW
   - Gas tank работает (но бесполезен, DEW неправильный)
   - DEW заменяет скорость (НЕПРАВИЛЬНО!)

✅ Phase 5: Hotbar Switching
   - Работает с хотбаром
```

---

## 🔧 ЧТО НУЖНО ИСПРАВИТЬ (Приоритет)

### TIER 1: BLOCKING (День 1)

#### Task 1.1: Отключить Автоматическое Притягивание
**Файл:** `src/main/java/com/armorberserk/daotcompat/physics/GrapplePhysicsController.java`

**Что делать:**
```
1. Найти место где velocity += (hook - player).normalize() * force
2. Заменить на: НЕ ТРОГАТЬ, только ограничивать расстояние
3. Движение только при SPACE (onClientTickEnd в DAOTCompat.java)
```

**Тест:**
```
ЛКМ → зацепиться
    → НЕ должен лететь
SPACE (удержание) → должно начаться подтягивание
```

**Оценка:** 1-2 часа

---

#### Task 1.2: Добавить Физику Натяжения
**Файл:** `GrapplePhysicsController.java` или новый `RopeConstraint.java`

**Что делать:**
```
1. Каждый тик проверять: distance = |player - hook|
2. ЕСЛИ distance > MAX_ROPE_LENGTH (48):
   - Вернуть игрока на сферу радиусом MAX_ROPE_LENGTH
   - Убрать компоненту скорости направленную от крюка
   - Сохранить тангенциальную скорость (маятник)
3. ЕСЛИ distance < MAX_ROPE_LENGTH:
   - Ничего не делать, свободное движение
```

**Математика есть в AUDIT_REPORT**

**Тест:**
```
Зацепиться
Лететь в сторону → игрок не может улететь дальше 48 блоков
Попытаться пройти через крюк → отскочит назад
```

**Оценка:** 2-3 часа

---

#### Task 1.3: Добавить Коллизию Троса (Обрыв)
**Файл:** Новый `RopeCollisionDetector.java` или в `HookTransformResolver.java`

**Что делать:**
```
1. Каждый тик сделать raycast от крюка к игроку
2. ЕСЛИ raycast попал в блок (не сам крюк):
   - disengageHook() - оборвать трос
   - Игрок падает под гравитацией
```

**Код дан в AUDIT_REPORT (Phase 3)**

**Тест:**
```
Зацепиться сквозь блок → трос рвется
Зацепиться нормально → работает
```

**Оценка:** 1-2 часа

---

### TIER 2: HIGH PRIORITY (День 2)

#### Task 2.1: Переписать DEW
**Файл:** `src/main/java/com/armorberserk/daotcompat/physics/DEWImpulseCalculator.java`

**Что делать:**
```
БЫЛО:
  velocity = lookDirection * HUGE_FORCE  // Замена

СТАЛО:
  currentVelocity = player.getDeltaMovement()
  additionalForce = lookDirection * DEW_STRENGTH
  newVelocity = currentVelocity + additionalForce  // Добавление
  player.setDeltaMovement(newVelocity)
```

**Код дан в AUDIT_REPORT (Phase 4)**

**Тест:**
```
Лететь быстро + DEW → скорость РАСТЕТ (не меняется)
DEW в разные стороны → движение плавное
```

**Оценка:** 1 час

---

#### Task 2.2: Правильная Физика SPACE (Подтягивание)
**Файл:** `GrapplePhysicsController.java`

**Что делать:**
```
SPACE (удержание):
  - Сокращать currentRopeLength медленно
  - Применять новое ограничение длины
  - Расходовать газ

SHIFT (удержание):
  - Удлинять currentRopeLength
  - Максимум MAX_ROPE_LENGTH
  - Восстанавливать газ? (опционально)
```

**Код дан в AUDIT_REPORT (Phase 5)**

**Тест:**
```
SPACE → плавное подтягивание
SHIFT → плавное отпускание
Комбинация → раскачка возможна
```

**Оценка:** 1-2 часа

---

### TIER 3: OPTIONAL (День 3, если есть время)

#### Task 3.1: Инерция Маятника
**Файл:** `GrapplePhysicsController.java`

**Что делать:**
- Сохранять тангенциальную скорость
- Ограничивать только радиальную скорость
- Дать gravity работать правильно

**Код дан в AUDIT_REPORT (Phase 6)**

**Оценка:** 2-3 часа

---

#### Task 3.2: Визуальные Эффекты (Спарки, Звуки)

Только ПОСЛЕ того как физика правильная!

---

## 📁 ФАЙЛЫ ДЛЯ РЕДАКТИРОВАНИЯ

```
CRITICAL:
  ├── GrapplePhysicsController.java (основные изменения)
  ├── DEWImpulseCalculator.java (переписать)
  └── HookTransformResolver.java (добавить коллизию)

REFERENCE:
  ├── PHYSICS_ARCHITECTURE_AUDIT.md (описание проблем + код)
  ├── GrappleStateManager.java (логика keybind состояния)
  └── DAOTCompat.java (основной класс мода)

BUILD:
  └── build.gradle.kts (не менять, работает)
```

---

## ⚡ БЫСТРЫЙ СТАРТ ДЛЯ НОВОЙ СЕССИИ

1. **Прочитать PHYSICS_ARCHITECTURE_AUDIT.md** (20 минут)
   - Понять что не так с физикой
   - Понять что нужно исправить

2. **Прочитать текущий GrapplePhysicsController.java** (10 минут)
   - Увидеть как сейчас работает
   - Понять что менять

3. **Задача 1.1** - Отключить автоматическое притягивание (1-2 ч)
   - Самое критичное
   - Даст базу для остального

4. **Задача 1.2** - Физика натяжения (2-3 ч)
   - Сложнее, но все коды даны
   - Главное - тестировать

5. **Задача 1.3** - Коллизия (1-2 ч)
   - Проще, raycast в Java/Minecraft стандартный

6. **Задача 2.1** - Переписать DEW (1 ч)
   - Просто найти и заменить логику

7. **Задача 2.2** - SPACE/SHIFT физика (1-2 ч)
   - Логика с currentRopeLength

**Итого:** ~8-14 часов работы на основное

---

## 🧪 ТЕСТИРОВАНИЕ ПОСЛЕ КАЖДОЙ ЗАДАЧИ

```
После 1.1:
  mvn clean package
  Запустить Minecraft
  Зацепиться → НЕ должен лететь ✅

После 1.2:
  Летать вокруг крюка → натяжение работает ✅
  
После 1.3:
  Зацепиться сквозь блок → трос рвется ✅

После 2.1:
  DEW + быстрое полет → скорость растет ✅

После 2.2:
  SPACE/SHIFT → плавное движение ✅
```

---

## 📊 ЧЕКЛИСТ ДЛЯ СЕССИИ

```
[ ] День 1
    [ ] Прочитать AUDIT
    [ ] Task 1.1 - Отключить авто-притяжение
    [ ] Task 1.2 - Физика натяжения
    [ ] Task 1.3 - Коллизия троса

[ ] День 2
    [ ] Task 2.1 - Переписать DEW
    [ ] Task 2.2 - SPACE/SHIFT физика
    [ ] Comprehensive testing

[ ] Опционально
    [ ] Task 3.1 - Инерция маятника
    [ ] Визуальные эффекты
    [ ] Balance tuning
```

---

## 📝 КОММИТ СООБЩЕНИЯ ДЛЯ КАЖДОЙ ЗАДАЧИ

```
Task 1.1: Disable auto-grapple attraction
"fix: remove automatic player attraction to hook

Player now stays in place after grappling.
Only Space key triggers pulling motion.
This fixes the 'magnet effect' issue."

Task 1.2: Rope tension physics
"feat: implement proper rope length constraint

- Constrains player to sphere of MAX_ROPE_LENGTH
- Preserves tangential velocity (pendulum physics)
- Removes radial velocity toward hook
- Prevents teleportation/snapping"

...etc
```

---

## 🎯 ФИНАЛЬНАЯ ЦЕЛЬ

**v2.0.0 - Physics Overhaul**

Движение будет:
- ✅ Плавное и предсказуемое
- ✅ Похожее на ODM из аниме
- ✅ С правильной инерцией
- ✅ С контролируемым подтягиванием
- ✅ С реалистичным маятником

---

## 📞 ЕСЛИ ЧТО-ТО НЕПОНЯТНО

1. Прочитать PHYSICS_ARCHITECTURE_AUDIT.md еще раз
2. Посмотреть видео которое анализировалось
3. Запустить мод текущей версии и увидеть как работает
4. Прочитать код GrapplePhysicsController (как сейчас)
5. Понять что менять

Все коды для исправлений даны в AUDIT!

---

**Подготовлено:** AI Developer + Video Analysis  
**Дата:** 2026-07-25  
**Статус:** READY FOR NEXT SESSION  
**Commit:** bb029f2  
**Branch:** round3-stage1-fixes
