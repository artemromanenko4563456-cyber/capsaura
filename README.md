# CapsAura (Fabric, Minecraft 26.2)

Kill aura, включается/выключается клавишей Caps Lock (меняется в Настройки → Управление → CapsAura).

## Сборка
Нужны Java 25 и Gradle 9.5.1+.

    gradle wrapper --gradle-version 9.5.1
    ./gradlew build        (Windows: gradlew.bat build)

Готовый файл: build/libs/capsaura-1.0.0.jar -> положить в папку mods (рядом с Fabric API 26.2).

## Настройки
В CapsAuraClient.java: RANGE, ATTACK_PASSIVE, ATTACK_PLAYERS. После изменения пересобрать.
