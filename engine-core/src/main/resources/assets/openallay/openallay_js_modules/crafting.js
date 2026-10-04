const compatibleItems = requirement => {
  const ids = requirement.alternatives.flatMap(alternative => {
    var resolved = alternative.resolvedItems ?? [];
    return alternative.kind === "item"
      ? [alternative.id].concat(Array.from(resolved))
      : Array.from(resolved);
  });
  return Array.from(new Set(ids)).sort();
};

const requirements = recipe =>
  Array.from(recipe.ingredients).concat(Array.from(recipe.catalysts));

const inventoryCounts = inventory => {
  const counts = Object.create(null);
  const add = stack => {
    if (stack && stack.count > 0 && stack.itemId !== "minecraft:air") {
      counts[stack.itemId] = (counts[stack.itemId] ?? 0) + Number(stack.count);
    }
  };
  inventory.slots.forEach(slot => add(slot.stack));
  add(inventory.offHand);
  return counts;
};

function Dinic(nodes) {
  this.graph = [];
  this.level = [];
  this.next = [];
  for (var index = 0; index < nodes; index++) {
    this.graph.push([]);
    this.level.push(-1);
    this.next.push(0);
  }
}

Dinic.prototype.addEdge = function(from, to, capacity) {
  const forward = {
    to,
    reverse: this.graph[to].length,
    initialCapacity: capacity,
    capacity
  };
  const reverse = {
    to: from,
    reverse: this.graph[from].length,
    initialCapacity: 0,
    capacity: 0
  };
  this.graph[from].push(forward);
  this.graph[to].push(reverse);
  return forward;
};

Dinic.prototype.buildLevels = function(source, sink) {
  this.level.fill(-1);
  this.level[source] = 0;
  const queue = [source];
  for (var cursor = 0; cursor < queue.length; cursor++) {
    var currentNode = queue[cursor];
    this.graph[currentNode].forEach(edge => {
      if (edge.capacity > 0 && this.level[edge.to] < 0) {
        this.level[edge.to] = this.level[currentNode] + 1;
        queue.push(edge.to);
      }
    });
  }
  return this.level[sink] >= 0;
};

Dinic.prototype.push = function(node, sink, limit) {
  if (node === sink) return limit;
  const edges = this.graph[node];
  while (this.next[node] < edges.length) {
    var edge = edges[this.next[node]];
    if (edge.capacity > 0 && this.level[edge.to] === this.level[node] + 1) {
      var pushed = this.push(edge.to, sink, Math.min(limit, edge.capacity));
      if (pushed > 0) {
        edge.capacity -= pushed;
        this.graph[edge.to][edge.reverse].capacity += pushed;
        return pushed;
      }
    }
    this.next[node]++;
  }
  return 0;
};

Dinic.prototype.maxFlow = function(source, sink) {
  let result = 0;
  while (this.buildLevels(source, sink)) {
    this.next.fill(0);
    let pushed;
    while ((pushed = this.push(source, sink, Number.MAX_SAFE_INTEGER)) > 0) {
      result += pushed;
    }
  }
  return result;
};

const allocateOnce = (recipe, available, crafts) => {
  const all = requirements(recipe);
  const consumed = all
    .filter(requirement => requirement.consumed)
    .map(requirement => requirement)
    .sort((left, right) => String(left.key).localeCompare(String(right.key)));
  const missing = [];

  all.filter(requirement => !requirement.consumed)
    .map(requirement => requirement)
    .sort((left, right) => String(left.key).localeCompare(String(right.key)))
    .forEach(requirement => {
      var present = compatibleItems(requirement)
        .reduce((total, item) => total + (available[item] ?? 0), 0);
      var allocated = Math.min(present, Number(requirement.count));
      if (allocated < requirement.count) {
        missing.push({
          requirementKey: requirement.key,
          required: Number(requirement.count),
          allocated,
          missing: Number(requirement.count) - allocated,
          alternatives: compatibleItems(requirement)
        });
      }
    });

  if (consumed.length === 0) {
    return {allocations: [], missing};
  }

  const items = Array.from(new Set(consumed.flatMap(compatibleItems))).sort();
  const source = 0;
  const requirementStart = 1;
  const itemStart = requirementStart + consumed.length;
  const sink = itemStart + items.length;
  const flow = new Dinic(sink + 1);
  const sourceEdges = [];
  const allocationEdges = [];

  consumed.forEach((requirement, index) => {
    var required = Number(requirement.count) * crafts;
    if (!Number.isSafeInteger(required)) {
      throw new Error("craft count exceeds the safe integer range");
    }
    sourceEdges.push(flow.addEdge(source, requirementStart + index, required));
    allocationEdges.push(compatibleItems(requirement).map(item => ({
      item,
      edge: flow.addEdge(
        requirementStart + index,
        itemStart + items.indexOf(item),
        required)
    })));
  });
  items.forEach((item, index) => {
    flow.addEdge(itemStart + index, sink, available[item] ?? 0);
  });
  flow.maxFlow(source, sink);

  const allocations = [];
  consumed.forEach((requirement, index) => {
    allocationEdges[index].forEach(({item, edge}) => {
      var used = edge.initialCapacity - edge.capacity;
      if (used > 0) {
        allocations.push({
          requirementKey: requirement.key,
          itemId: item,
          count: used
        });
      }
    });
    var unfilled = sourceEdges[index].capacity;
    if (unfilled > 0) {
      var requiredTotal = sourceEdges[index].initialCapacity;
      missing.push({
        requirementKey: requirement.key,
        required: requiredTotal,
        allocated: requiredTotal - unfilled,
        missing: unfilled,
        alternatives: compatibleItems(requirement)
      });
    }
  });
  allocations.sort((left, right) =>
    String(left.requirementKey).localeCompare(String(right.requirementKey))
      || String(left.itemId).localeCompare(String(right.itemId)));
  missing.sort((left, right) =>
    String(left.requirementKey).localeCompare(String(right.requirementKey)));
  return {allocations, missing};
};

const maximumCrafts = (recipe, available) => {
  const all = requirements(recipe);
  if (!all.some(requirement => requirement.consumed)) {
    return allocateOnce(recipe, available, 1).missing.length === 0
      ? Number.MAX_SAFE_INTEGER
      : 0;
  }
  let low = 0;
  let high = 1;
  while (allocateOnce(recipe, available, high).missing.length === 0) {
    low = high;
    if (high > Number.MAX_SAFE_INTEGER / 2) return Number.MAX_SAFE_INTEGER;
    high *= 2;
  }
  while (low + 1 < high) {
    var middle = low + Math.floor((high - low) / 2);
    if (allocateOnce(recipe, available, middle).missing.length === 0) {
      low = middle;
    } else {
      high = middle;
    }
  }
  return low;
};

const recipeCost = recipe => {
  const consumed = requirements(recipe).filter(requirement => requirement.consumed);
  const catalysts = requirements(recipe).filter(requirement => !requirement.consumed);
  return {
    consumedSlots: consumed.length,
    consumedItems: consumed.reduce(
      (total, requirement) => total + Number(requirement.count), 0),
    catalystSlots: catalysts.length,
    catalystItems: catalysts.reduce(
      (total, requirement) => total + Number(requirement.count), 0),
    fluidInputs: recipe.fluids.length
  };
};

const allocate = (recipe, inventory, requestedCrafts = 1) => {
  const crafts = Number(requestedCrafts);
  if (!Number.isSafeInteger(crafts) || crafts <= 0) {
    throw new Error("requestedCrafts must be a positive safe integer");
  }
  const available = inventoryCounts(inventory);
  const result = allocateOnce(recipe, available, crafts);
  return {
    craftable: result.missing.length === 0,
    conclusive:
      String(recipe.evidence.completeness) === "COMPLETE"
      && String(inventory.evidence.completeness) === "COMPLETE",
    requestedCrafts: crafts,
    maximumCrafts: maximumCrafts(recipe, available),
    allocations: result.allocations,
    missing: result.missing
  };
};

module.exports = Object.freeze({
  recipeCost,
  allocate
});
