# SimpleShop

A GUI shop for **Paper / Folia 26.3**. Players buy and sell common vanilla items through
chest menus, paid through any economy plugin that hooks into Vault.

## Features

- **Shop GUI** (`/shop`): categories → paged items → amount picker
  - Left-click to buy, shift-left to buy a stack, right-click to sell, shift-right to sell all
  - **Custom amount**: type any number in a pop-up dialog
  - **Confirm screen** for big transactions and command items. The confirmed price is guaranteed;
    if the market moves in the meantime, the player is asked again.
- **Search**: the compass button in the main menu, or `/shop search <name>`
- **Sell box** (`/sell`), `/sell hand`, `/sell all`
- **`/worth`** (`/worth all`, `/shop worth`): what the held stack / whole inventory sells for
- **Dynamic pricing**: selling lowers a price, buying raises it, and prices drift back to base over
  time. Transactions are priced unit by unit along the curve, so splitting sales gains nothing, and
  buy > sell always holds, so there is no buy-sell arbitrage.
- **Command items**: ranks, kits, XP, anything. They run console commands with `{player}`, `{uuid}`, `{amount}`.
- **In-game editor** (`/shop edit`): click an item to change its prices or reset its market price,
  shift-right-click to remove it, or click an item in your own inventory to add it. Changes are saved to `shop.yml`.
- **Transaction log**: daily files in `plugins/SimpleShop/logs/`, plus `/shop log [player]` in game
- **Configurable layout**: every menu's title, size, filler, button slots, materials and text in `menus.yml`
- **PlaceholderAPI** (optional), see below
- About 185 items with ready-to-use prices. Crafting/smelting can't be used to make money from the shop.
- **Economy** is detected automatically through **Vault** or **VaultUnlocked** (EssentialsX, CMI, TheNewEconomy, XConomy, CoinsEngine, ...)
- Folia-safe: player actions run on the player's scheduler, console commands on the global scheduler

## Install

1. Build: `./gradlew build` → `build/libs/SimpleShop-1.2.0.jar`
2. Put it in `plugins/` with **Vault** (Paper) or **VaultUnlocked** (Paper/Folia) and an economy plugin.
   PlaceholderAPI is optional.
3. Start the server, then edit `plugins/SimpleShop/{config,shop,menus}.yml` and run `/shop reload`.

## Commands & permissions

| Command | Permission | Default |
|---|---|---|
| `/shop [category]`, `/shop search <query>` (alias `/store`) | `simpleshop.use` | everyone |
| buying | `simpleshop.buy` | everyone |
| `/sell [hand\|all]`, selling | `simpleshop.sell` | everyone |
| `/worth [all]`, `/shop worth [all]` | `simpleshop.worth` | everyone |
| `/shop edit` | `simpleshop.edit` | op |
| `/shop log [player]` | `simpleshop.log` | op |
| `/shop reload`, `/shop resetprices [item]` | `simpleshop.admin` (includes edit + log) | op |

EssentialsX also has `/sell` and `/worth`. SimpleShop's versions take priority; Essentials' are
still available as `/essentials:sell` and `/essentials:worth`.

## PlaceholderAPI

`ITEM` is a material name (`diamond`) or a command item id.

| Placeholder | Value |
|---|---|
| `%simpleshop_buy_ITEM%` / `%simpleshop_buy_raw_ITEM%` | current buy price (formatted / plain number) |
| `%simpleshop_sell_ITEM%` / `%simpleshop_sell_raw_ITEM%` | current sell price |
| `%simpleshop_base_buy_ITEM%` / `%simpleshop_base_sell_ITEM%` | base price from shop.yml |
| `%simpleshop_multiplier_ITEM%` | dynamic price multiplier, e.g. `0.85` |
| `%simpleshop_trend_ITEM%` | e.g. `▲ +12%` |
| `%simpleshop_worth%` / `%simpleshop_worth_raw%` | sell value of the player's inventory |
| `%simpleshop_worth_hand%` / `%simpleshop_worth_hand_raw%` | sell value of the held stack |

## shop.yml

```yaml
categories:
  building:
    name: "<gold>Building Blocks"
    icon: BRICKS
    slot: 10
    items:
      STONE: { buy: 2, sell: 0.5 }
      RAW_IRON: { sell: 6 }                 # sell-only
      DIAMOND: { buy: 250, sell: 75, volume: 500, max-amount: 64 }
      COBBLESTONE: { buy: 1, sell: 0.25, dynamic: false }
  services:
    name: "<yellow>Services"
    icon: EXPERIENCE_BOTTLE
    items:
      vip_rank:                             # command item: any unique id
        material: DIAMOND_CHESTPLATE
        name: "<aqua>VIP Rank"
        lore: [ "<gray>Unlocks /kit vip" ]
        buy: 50000
        max-amount: 1
        permission: shop.buy.vip            # optional
        commands: [ "lp user {player} parent add vip" ]
```

Dynamic pricing: with the default `money-volume: 20000`, selling about 270 diamonds (or about 80,000
cobblestone) halves the price, and it recovers halfway every 6 hours. Prices stay between 0.25× and 3×
the base. Market state is stored in `data/market.yml`.

## Updating

Replace the jar and restart. On every start (and `/shop reload`) SimpleShop updates its own files:

- **config.yml / menus.yml**: new settings are added with their comments; your values are kept.
  Settings that no longer do anything are listed in a console warning.
- **shop.yml**: new default items and categories are added. `data/shop-defaults.yml` remembers
  what was already offered, so items or categories you deleted are never re-added, and your prices are never changed.
- **1.0.0 → newer**: menu settings in `config.yml` are moved to `menus.yml`.
- Every changed file is backed up to `backups/<name>-<date>.yml` first.

## Tests

`./gradlew test` runs unit tests for the pricing math (no arbitrage, bulk sale = split sales,
clamping, decay, persistence) and for price parsing.

## Adding integrations

Economy access goes through `economy/EconomyProvider`. To add a direct hook, implement it and
select it in `economy/Economies`. PlaceholderAPI code is isolated in `integration/`.
