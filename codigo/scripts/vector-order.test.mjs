import test from 'node:test';
import assert from 'node:assert/strict';
import { relation } from './vector-order.mjs';
test('causalidade, concorrência, igualdade e validação', () => {
  assert.equal(relation([3,1,0],[3,2,0]), 'ANTES');
  assert.equal(relation([3,2,0],[3,1,0]), 'DEPOIS');
  assert.equal(relation([3,1,0],[1,3,0]), 'CONCORRENTES');
  assert.equal(relation([3,1,0],[3,1,0]), 'IGUAIS');
  assert.throws(()=>relation([1],[1,2,3]));
});
