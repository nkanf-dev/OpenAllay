# Programmatic analysis patterns

## Rank

```js
return rows
  .filter(row => Number.isFinite(row.score))
  .sort((a, b) => b.score - a.score)
  .slice(0, 10)
  .map(({id, score}) => ({id, score}));
```

## Group and aggregate

```js
const groups = helpers.groupBy(rows, row => row.namespace);
return Object.entries(groups)
  .map(([namespace, values]) => ({namespace, count: values.length}))
  .sort((a, b) => b.count - a.count);
```

## Join

```js
const items = new Map(mc.items.map(item => [item.id, item]));
return mc.recipes
  .map(recipe => ({recipe, output: items.get(recipe.outputs?.[0]?.stack?.itemId)}))
  .filter(row => row.output?.namespace === "example")
  .map(row => ({recipeId: row.recipe.id, output: row.output.displayName}));
```

## Flatten nested arrays

```js
return mc.items.flatMap(item =>
  (item.properties.effects ?? []).map(effect => ({itemId: item.id, ...effect})));
```

## Continue from a large result

Pass `["r_exact_handle"]` in the Tool's `handles` field:

```js
const previous = workspace.open("r_exact_handle");
return previous.filter(row => row.score > 0).slice(0, 20);
```

Never copy or invent a handle. Never return the whole reopened value unchanged.
