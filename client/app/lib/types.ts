export type Fighter = {
  id: number;
  name: string;
  photo: string | null;
  status: string;
  lastPrice: number;
};

export type PriceLevel = { price: number; quantity: number };
export type Orderbook = { bids: PriceLevel[]; asks: PriceLevel[] };
export type Trade = { price: number; quantity: number; executedAt: string };

export type Order = {
  id: number;
  fighterId: number;
  fighterName: string;
  side: "BUY" | "SELL";
  type: "LIMIT" | "MARKET";
  limitPrice: number | null;
  quantity: number;
  filledQuantity: number;
  status: "OPEN" | "PARTIAL" | "FILLED" | "CANCELLED";
  createdAt: string;
};
