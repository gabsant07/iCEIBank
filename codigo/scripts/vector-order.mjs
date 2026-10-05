export function relation(a, b) {
  for (const v of [a, b]) {
    if (!Array.isArray(v) || v.length !== 3 || v.some(n => !Number.isSafeInteger(n) || n < 0)) throw new Error('Vetor inválido');
  }
  const before = a.every((n, i) => n <= b[i]);
  const after = b.every((n, i) => n <= a[i]);
  return before && after ? 'IGUAIS' : before ? 'ANTES' : after ? 'DEPOIS' : 'CONCORRENTES';
}
