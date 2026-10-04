# Rhino runtime examples

These paths are examples, not a closed schema. Discover the actual captured
shape when it differs.

For the three workflows below, the documented fixture and normal captured data
use these exact stable roots and fields. Execute the matching program directly.
Do not precede it with root, array, key, sample, or per-row discovery calls.

## Highest-damage sword

Call `run_javascript` with the following program.

```js
var ranked = mc.items
  .filter(item => item.id.toLowerCase().includes("sword"))
  .map(item => {
    var modifiers = item.properties["minecraft:attribute_modifiers"];
    var damage = null;
    if (Array.isArray(modifiers)) {
      for (var index = 0; index < modifiers.length; index++) {
        if (modifiers[index].type === "minecraft:attack_damage"
            && modifiers[index].id === "minecraft:base_attack_damage"
            && modifiers[index].slot === "mainhand") {
          damage = Number(modifiers[index].amount);
          break;
        }
      }
    }
    return {id: item.id, name: item.displayName, damage: damage};
  })
  .filter(item => Number.isFinite(item.damage))
  .sort((left, right) =>
    (right.damage - left.damage) || left.id.localeCompare(right.id));
return ranked;
```

Minecraft 26.2 does not expose a universal `minecraft:swords` item tag in the
captured registry. The stable sword discriminator available to this workflow is
the conventional `sword` resource path, while the score itself comes from the
structured main-hand `minecraft:attribute_modifiers` component. Do not replace
that component read with display-name guessing. Return the complete ranking.
Small candidate sets are shown in full; larger rankings stay complete in the
request workspace and the Tool result provides the exact handle needed for a
later focused projection.

## Least-material container recipe

Call `run_javascript` with the following program.

```js
const items = new Map(mc.items.map(item => [item.id, item]));
const crafting = require("openallay:crafting");
return mc.recipes
  .filter(recipe => {
    const outputId = recipe.outputs?.[0]?.stack?.itemId;
    const output = items.get(outputId);
    return output?.tags?.some(tag => tag.includes("container"));
  })
  .map(recipe => Object.assign({
    recipeId: recipe.id,
    output: recipe.outputs?.[0]?.stack?.itemId
  }, crafting.recipeCost(recipe)))
  .sort((a, b) =>
    (a.consumedItems - b.consumedItems)
    || (a.consumedSlots - b.consumedSlots)
    || String(a.recipeId).localeCompare(String(b.recipeId)));
```

The first row is the answer. This program already compares every captured
container recipe and returns all requested fields. Answer immediately from a
`scope: complete` result. Do not inspect that recipe again or reopen the result:
inventory sufficiency is unrelated to minimum ingredient units.

## Strongest poison effect and its production path

Call `run_javascript` with the following program.

```js
var poison = [];
for (var itemIndex = 0; itemIndex < mc.items.length; itemIndex++) {
  var item = mc.items[itemIndex];
  var effects = item.properties["minecraft:effects"] ?? [];
  for (var effectIndex = 0; effectIndex < effects.length; effectIndex++) {
    var effect = effects[effectIndex];
    if (String(effect.id).includes("poison")) {
      poison.push({
        itemId: item.id,
        duration: Number(effect.duration ?? 0),
        amplifier: Number(effect.amplifier ?? 0)
      });
    }
  }
}
poison.sort((left, right) =>
  (right.amplifier - left.amplifier)
  || (right.duration - left.duration)
  || left.itemId.localeCompare(right.itemId));
var best = poison[0];
return {
  best,
  recipes: mc.recipes.filter(recipe =>
    recipe.outputs?.some(output => output.stack?.itemId === best?.itemId))
};
```

If effects are stored under components or NBT-like nested data instead, inspect
`helpers.schema` once and adapt the property traversal; do not issue one Tool
call per item.
