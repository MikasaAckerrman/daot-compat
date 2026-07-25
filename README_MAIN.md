# 🎮 DAOT Aeronautics Compat - v1.3.4 MOBILE FIX

**Attack on Titan 3D Maneuver Gear для Minecraft 1.21.1**

---

## ⚡ БЫСТРЫЙ СТАРТ

### Скачай и установи
```bash
# Скачай это из Release
daotcompat-1.3.4-MOBILE-FIX.jar

# Помести в папку модов
~/.minecraft/mods/

# Требует
NeoForge 1.21.1 (21.1.240+)
Danny's AOT 2.4.2

# Запусти игру - готово!
```

---

## 📚 ДОКУМЕНТАЦИЯ

### 🟢 НАЧНИ С ЭТОГО (5 минут)
**→ [QUICK_REFERENCE.md](QUICK_REFERENCE.md)**  
- Горячие клавиши
- Как зацепляться
- Как управлять

### 🟡 ПОДРОБНОЕ РУКОВОДСТВО (30 минут)
**→ [GAMEPLAY_GUIDE.md](GAMEPLAY_GUIDE.md)**  
- Все механики в деталях
- Боевые сценарии
- Советы для профессионалов
- Maятник физика
- DEW импульс

### 🔴 ТЕХНИЧНЫЙ ОТЧЁТ (20 минут)
**→ [PROJECT_STATUS.md](PROJECT_STATUS.md)**  
- История версий
- Все исправленные баги
- Архитектура кода
- Какие файлы изменены

### 🔵 ПОЛНАЯ РАБОТА (15 минут)
**→ [WORK_SUMMARY.md](WORK_SUMMARY.md)**  
- Что было сделано
- Архитектурные решения
- Решённые проблемы
- Статистика

---

## 🎯 УПРАВЛЕНИЕ

| Клавиша | Действие |
|---------|----------|
| **ЛКМ** | Зацепить крюки |
| **Release ЛКМ** | Отпустить |
| **SPACE** | Подтягивание |
| **SHIFT** | Спуск |
| **W** | Ускорение |
| **Double-SPACE** | DEW импульс (газ) |

---

## ✅ ЧТО РАБОТАЕТ

- ✅ Две механические рукавицы с крюками
- ✅ Натяжение троса (constraint система)
- ✅ Управление длиной троса (SPACE/SHIFT)
- ✅ Маятник физика (сохранение инерции)
- ✅ DEW газовый импульс (двойной клик SPACE)
- ✅ Звуки и визуал
- ✅ Двойные крюки работают одновременно
- ✅ Оптимизирована (FPS стабильный)
- ✅ Мобильная поддержка (Zalith Launcher)

---

## 🐛 ИСПРАВЛЕНО В v1.3.4

| Версия | Что исправлено |
|--------|---|
| v1.3.1 | Двойные крюки + камера + ощущения |
| v1.3.2 | Отпуск мышью + спам звуков + лаги |
| v1.3.3 | Event bus crash |
| v1.3.4 | Abstract class crash (ТЕКУЩАЯ) |

---

## 🔧 ИДЕАЛЬНЫЙ МОДПАК

```
daotcompat-1.3.4-MOBILE-FIX.jar       ← ОСНОВНОЙ МОД
+ create-aeronautics-1.3.0.jar        ← Летающие корабли
+ sable-neoforge-1.21.1-2.0.3.jar     ← Динамические платформы
+ dannys-aot-2.4.2.jar                ← Враги Titans
+ sound-physics-aeronautics-0.2.0.jar ← Звук
= ПОЛ ATTAX ON TITAN!
```

---

## 📖 ГЕЙМПЛЕЙ СЦЕНАРИЙ

```
1. Входишь в игру
2. ЛКМ на стену → Крюки цепляются (TRIPWIRE_CLICK_ON)
3. SPACE → Летишь вверх к крюку
4. SHIFT → Спускаешься/контролируешь
5. W → Ускорение
6. Double-SPACE → DEW импульс (BLAZE_SHOOT)
7. Release ЛКМ → Отпускаешься (CHAIN_BREAK)
8. Летишь на врага с максимальной скоростью!
```

---

## 🎮 КАК РАБОТАЕТ МЕХАНИКА

### Зацепление
- Крюки летят в направлении взгляда
- Цепляются за первый твёрдый блок
- Расстояние: до 48 блоков
- Звук: двойной стерео (левый низко, правый высоко)

### Управление
- **SPACE:** currentRopeLength -= 0.4 блока/тик
- **SHIFT:** currentRopeLength += 0.2 блока/тик
- **Никогда:** Трос медленно возвращается к максимуму

### Маятник
- Тангенциальная скорость сохраняется
- Ты раскачиваешься как маятник
- В нижней точке нажимай SPACE для максимального взлёта

### DEW
- Формула: BASE × gas% × ropeMultiplier × speedMultiplier × altitude
- 1 крюк: ×1.6
- 2 крюка: ×2.3 (мощнее!)
- Расход: 15% газа

---

## ⚙️ ПАРАМЕТРЫ (в коде)

```java
MAX_ROPE_LENGTH = 48.0 блоков
MIN_ROPE_LENGTH = 2.0 блоков
REEL_SPEED = 0.4 блока/тик (подтягивание)
RELEASE_SPEED = 0.2 блока/тик (спуск)
SOUND_COOLDOWN_TICKS = 5 (между звуками)
COLLISION_CHECK_INTERVAL = 10 (раз в 10 тиков)
```

---

## 🚀 ТЕХНИЧЕСКИЕ ДЕТАЛИ

### Event Bus
- **Проблема:** @SubscribeEvent конфликтовал
- **Решение:** Lambda вместо регистрации
- **Результат:** Работает везде

### Mouse Input
- **Проблема:** InputEvent.MouseButton abstract
- **Решение:** Direct method call (MouseInputListener.tick())
- **Результат:** Отпуск мышью работает

### Optimization
- **Проблема:** Collision check лаги
- **Решение:** Проверка каждые 10 тиков
- **Результат:** FPS стабильный

### Sound
- **Проблема:** Спам звуков (20/сек)
- **Решение:** Cooldown система
- **Результат:** Каждый звук 1 раз

---

## 📊 СТАТИСТИКА

| Метрика | Значение |
|---------|----------|
| JAR размер | 65 KB |
| Новых файлов | 1 (MouseInputListener.java) |
| Изменённых файлов | 3 |
| Исправленных багов | 5 |
| Версий выпущено | 4 (v1.3.1-1.3.4) |
| Работающих механик | 100% |

---

## 🎓 ДЛЯ РАЗРАБОТЧИКОВ

### Файлы которые нужно знать

```
src/main/java/.../physics/
└─ GrapplePhysicsController.java      ← Основная механика
└─ DEWImpulseCalculator.java          ← Газовый импульс

src/main/java/.../input/
└─ KeybindEventListener.java          ← Слушатель клавиш
└─ MouseInputListener.java            ← Отслеживание мышки
└─ GrappleStateManager.java           ← Состояние

src/main/java/.../
└─ DAOTCompat.java                    ← Инициализация
```

### Если хочешь разобраться
1. Прочитай `PROJECT_STATUS.md` - история
2. Смотри `GrapplePhysicsController.java` - основной код
3. Смотри `KeybindEventListener.java` - как вызываются методы
4. Смотри `MouseInputListener.java` - как отслеживается мышь

---

## ❓ FAQ

**Q: Мод не загружается**  
A: Проверь NeoForge версию (21.1.240+)

**Q: Крюки не отпускаются**  
A: Отпусти просто ЛКМ (FIXED v1.3.2)

**Q: Звуки спамят**  
A: Это исправлено в v1.3.2 (cooldown)

**Q: Лаги**  
A: Убедись что v1.3.4 (не v1.3.2)

**Q: Работает на мобильном?**  
A: Да! v1.3.4 протестирована на Zalith Launcher

---

## 🔗 ССЫЛКИ

- **GitHub:** https://github.com/MikasaAckerrman/daot-compat
- **Branch:** `round3-stage1-fixes`
- **JAR:** `daotcompat-1.3.4-MOBILE-FIX.jar`
- **Requires:** NeoForge 1.21.1 + Danny's AOT 2.4.2

---

## 📋 ФАЙЛЫ В РЕПОЗИТОРИИ

```
📁 daot-compat/
├─ README_MAIN.md              ← ТЫ ЗДЕСЬ (обзор)
├─ QUICK_REFERENCE.md          ← Горячие клавиши (5 мин)
├─ GAMEPLAY_GUIDE.md           ← Полное руководство (30 мин)
├─ PROJECT_STATUS.md           ← История и статус (20 мин)
├─ WORK_SUMMARY.md             ← Техничный отчёт (15 мин)
├─ daotcompat-1.3.4-MOBILE-FIX.jar
├─ src/main/java/...           ← Исходный код
└─ build/libs/daotcompat-1.0.0.jar
```

---

**Версия:** v1.3.4-MOBILE-FIX  
**Дата:** 26 июля 2026  
**Статус:** ✅ PRODUCTION READY

Наслаждайся свободой летания как в Attack on Titan! 🎮✨
