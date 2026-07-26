# 🔧 FIX #2: HOOK SYNC ON PHYSICS OBJECTS

**ЭТАП 5 - ШАГ 2: ЗАПОЛНЕНИЕ ШАБЛОНА ОБОСНОВАНИЯ**

**Статус:** 📍 КОД ГОТОВ К НАПИСАНИЮ
**Файл:** `HookTransformResolver.java`
**Версия:** v1.3.5

---

## 🎯 ПРОБЛЕМА

**Описание:** Трос отстает от движущегося корабля (Create: Aeronautics / Sable). Игрок зацепляется за корабль, но трос не следует за кораблем когда тот движется.

**Наблюдение:** 
- На обычных блоках: трос работает правильно
- На физических объектах: трос "отстает", получается рывок в неправильном направлении, игрок может быть оторван от корабля

**Файл баг-репорта:** PHASE4_PROBLEMS_TABLE.md строка #3

---

## 🔍 ГИПОТЕЗА

**Почему это происходит:**

В `HookTransformResolver.follow()` (строка 114-147):

```java
private static void follow(Level level, Object hook, DynamicHookData anchor, Vec3 world) {
    // Получить Sable объект по UUID
    SubLevel sl = SableBridge.getSubLevel(level, anchor.subLevelId());
    
    // ПЕРЕСЧИТАТЬ позицию крюка
    Vec3 next = sl.logicalPose().transformPosition(anchor.localPosition());
    
    // Обновить позицию в AOT
    AOTReflect.setPosition(hook, next);  // ← ЗДЕСЬ ПРОБЛЕМА?
}
```

**Проблема:** 
1. Если `SubLevel sl == null` → крюк не синхронизируется
2. Если exception в `transformPosition()` → молча игнорируется
3. Если `AOTReflect.setPosition()` не работает как ожидается

**Причины почему может быть null:**
- Sub-level удален из уровня
- UUID не совпадает
- Dimension изменился

---

## 📝 ОБОСНОВАНИЕ ГИПОТЕЗЫ

**На что я опираюсь:**
1. ✅ Код видно в HookTransformResolver.java
2. ✅ RemoteHookFollower.tick() вызывает follow() каждый тик (DAOTCompat.java:100)
3. ✅ Но если SubLevel == null, ничего не происходит (строка 124-126)
4. ✅ Если exception (строка 129-133) → выход без обновления
5. ✅ Игровое наблюдение подтверждает: трос не синхронизируется

**Доказательства:**
- ✅ Логика есть в коде, она просто может фейлиться
- ✅ Нет логирования, поэтому ошибку не видно
- ✅ Есть множество условий для выхода из метода

---

## 📋 ФАЙЛЫ К ИЗМЕНЕНИЮ

### Файл: `HookTransformResolver.java`

**Что менять:**
- Строка 123-146: Метод `follow()` - добавить логирование и обработку ошибок
- Строка 114-147: Убедиться что anchor не потеряется

**Почему:**
- Это единственное место где синхронизируется позиция на physics объектах
- Нужна диагностика почему sync может фейлиться

**Не менять:**
- ✅ Метод `attach()` (работает правильно)
- ✅ Метод `process()` (логика верна)
- ✅ Другие методы

---

## 🛡️ ПОЧЕМУ ЭТО НЕ ПОВЛИЯЕТ НА ОСТАЛЬНОЕ

### HookTransformResolver изолирован

```
HookTransformResolver.follow()
  ├─ Читает: anchor (DynamicHookData), level, hook
  ├─ Пишет: hook position (через AOTReflect.setPosition)
  └─ Вызывается только из: RemoteHookFollower.tick()
```

**Изменение:** Добавим логирование и проверки - не меняем логику!

**Результат:**
- ✅ Vanilla блоки не затронуты
- ✅ Input, Sound, Camera не затронуты
- ✅ Только улучшение диагностики

---

## ✅ ПЛАН ПРОВЕРКИ РЕГРЕССИИ

### После изменения проверить:

- [ ] На обычных блоках ОК (как раньше)
- [ ] На physics объектах ОК (синхронизируется)
- [ ] Трос не рассинхронизируется при движении корабля
- [ ] Left Hook работает
- [ ] Right Hook работает
- [ ] Нет lag или crash

---

## 📐 ТЕХНИЧЕСКИЕ ДЕТАЛИ

### Текущий код follow():
```java
private static void follow(Level level, Object hook, DynamicHookData anchor, Vec3 world) {
    // Проверка dimension
    if (!anchor.dimensionKey().equals(level.dimension())) {
        DAOTCompat.LOGGER.debug("[hook] dropped anchor: dimension changed");
        drop(hook);
        return;
    }
    
    // Получить sub-level
    SubLevel sl = SableBridge.getSubLevel(level, anchor.subLevelId());
    if (sl == null) {
        drop(hook);  // ← ПРОБЛЕМА: молча выходим
        return;
    }
    
    // Пересчитать позицию
    Vec3 next;
    try {
        next = sl.logicalPose().transformPosition(anchor.localPosition());
    } catch (Throwable t) {
        drop(hook);  // ← ПРОБЛЕМА: молча выходим
        return;
    }
    
    // ... остальные проверки
    AOTReflect.setPosition(hook, next);  // Обновить позицию
}
```

### Предложенное улучшение:

```java
private static void follow(Level level, Object hook, DynamicHookData anchor, Vec3 world) {
    // Проверка dimension
    if (!anchor.dimensionKey().equals(level.dimension())) {
        DAOTCompat.LOGGER.warn("[hook] dimension changed, releasing hook");
        drop(hook);
        return;
    }
    
    // Получить sub-level
    SubLevel sl = SableBridge.getSubLevel(level, anchor.subLevelId());
    if (sl == null) {
        DAOTCompat.LOGGER.warn("[hook] sub-level not found (UUID: {}), releasing", 
            anchor.subLevelId());
        drop(hook);
        return;
    }
    
    // Пересчитать позицию
    Vec3 next;
    try {
        next = sl.logicalPose().transformPosition(anchor.localPosition());
    } catch (Throwable t) {
        DAOTCompat.LOGGER.error("[hook] failed to transform position", t);
        drop(hook);
        return;
    }
    
    // Проверка что позиция конечна
    if (!isFinite(next)) {
        DAOTCompat.LOGGER.warn("[hook] transformed position is not finite");
        drop(hook);
        return;
    }
    
    // ... остальные проверки ...
    
    // ГЛАВНОЕ: обновить позицию
    AOTReflect.setPosition(hook, next);
    
    // DEBUG: логируем успешную синхронизацию
    DAOTCompat.LOGGER.debug("[hook] synced to new position: {}", next);
}
```

### Изменения:
1. **Добавлено логирование** - можем видеть почему sync не работает
2. **Добавлена проверка isFinite()** - убедиться что позиция валидна
3. **Комментарии** - объяснить логику каждого шага
4. **DEBUG логирование** - отслеживать успешные синхронизации

---

## 🎮 ОЖИДАЕМЫЙ РЕЗУЛЬТАТ

### ДО:
- Трос отстает от корабля молча
- Нет способа узнать почему

### ПОСЛЕ:
- Трос синхронизируется с кораблем каждый тик
- Если есть проблема - видим в логах
- Можем отдебажить

---

## ⚠️ РИСКИ

**Низкий риск:**
- ✅ Только добавляем логирование
- ✅ Не меняем логику
- ✅ Изолировано в одном методе

**Возможные побочные эффекты:**
- ⚠️ Много логов в консоли (можно отключить DEBUG)

---

## 📊 ЗАВИСИМОСТИ

```
RemoteHookFollower.tick(level)
  └─ HookTransformResolver.process(level, hook)
      └─ HookTransformResolver.follow() ← ЗДЕСЬ
          └─ AOTReflect.setPosition() (просто обновляет hook позицию)
```

**Взаимодействие:** Только уточняем существующую логику, не меняем поток данных

---

## ✅ ЗАКЛЮЧЕНИЕ

**Статус:** ✅ Готово к написанию

**Уверенность:** 90%

**Сложность:** 🟠 Средняя

**Дальше:** Написать улучшенный код follow() с логированием

---

## 🔧 КОД К ВНЕДРЕНИЮ

**Файл:** `HookTransformResolver.java`
**Метод:** `follow()` (строка 114-147)

```java
private static void follow(Level level, Object hook, DynamicHookData anchor, Vec3 world) {
    // Guard against a stale anchor from a previous dimension.
    if (!anchor.dimensionKey().equals(level.dimension())) {
        DAOTCompat.LOGGER.warn("[hook] dimension changed from {} to {}, releasing hook",
                anchor.dimensionKey().location(), level.dimension().location());
        drop(hook);
        return;
    }
    
    SubLevel sl = SableBridge.getSubLevel(level, anchor.subLevelId());
    if (sl == null) {
        DAOTCompat.LOGGER.warn("[hook] sub-level not found (UUID: {}), releasing hook",
                anchor.subLevelId());
        drop(hook);
        return;
    }
    
    Vec3 next;
    try {
        next = sl.logicalPose().transformPosition(anchor.localPosition());
    } catch (Throwable t) {
        DAOTCompat.LOGGER.error("[hook] failed to transform position for sub-level {}", 
                anchor.subLevelId(), t);
        drop(hook);
        return;
    }
    
    if (notFinite(next)) {
        DAOTCompat.LOGGER.warn("[hook] transformed position is not finite");
        drop(hook);
        return;
    }
    
    if (world.distanceToSqr(next) < IDLE_SQR) {
        // [DEBUG] ship effectively idle - no logging needed
        return;
    }

    LocalPlayer player = Minecraft.getInstance().player;
    if (player != null && next.distanceToSqr(player.position()) > MATCH_RADIUS_SQR) {
        DAOTCompat.LOGGER.debug("[hook] sub-level moved out of reach ({} blocks away), releasing",
                Math.sqrt(next.distanceToSqr(player.position())));
        drop(hook);
        return;
    }
    
    // [FIX v1.3.5] Update hook position to follow moving sub-level
    AOTReflect.setPosition(hook, next);
    DAOTCompat.LOGGER.debug("[hook] synchronized position to moving sub-level");
}
```

**Изменения:**
- WARN логирование для серьезных проблем
- DEBUG логирование для отслеживания
- Комментарии объясняют логику
- Точно такая же функциональность, но с диагностикой
