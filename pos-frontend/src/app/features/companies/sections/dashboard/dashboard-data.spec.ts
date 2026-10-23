import { describe, expect, it } from 'vitest';
import { dailySales } from './dashboard-data';

describe('dailySales', () => {
  it('ordena datos recibidos y completa dias sin ventas al cruzar de mes', () => {
    expect(
      dailySales(
        [
          { fecha: '2026-03-01', total: 40 },
          { fecha: '2026-02-27', total: 10 },
        ],
        3,
        '2026-03-01',
      ),
    ).toEqual([
      { fecha: '2026-02-27', total: 10 },
      { fecha: '2026-02-28', total: 0 },
      { fecha: '2026-03-01', total: 40 },
    ]);
  });
  it('suma registros del mismo dia y conserva importes negativos', () => {
    expect(
      dailySales(
        [
          { fecha: '2026-10-23', total: 25 },
          { fecha: '2026-10-23', total: -30 },
        ],
        1,
        '2026-10-23',
      ),
    ).toEqual([{ fecha: '2026-10-23', total: -5 }]);
  });
  it('excluye datos fuera del periodo y valores no finitos', () => {
    expect(
      dailySales(
        [
          { fecha: '2026-10-20', total: 99 },
          { fecha: '2026-10-23', total: NaN },
        ],
        2,
        '2026-10-23',
      ),
    ).toEqual([
      { fecha: '2026-10-22', total: 0 },
      { fecha: '2026-10-23', total: 0 },
    ]);
  });
});
