/** Balances/prices are stored in sub-units (1 coin = 100). Show as coins, 2 dp. */
export function formatCoins(subunits: number): string {
  return (subunits / 100).toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}
