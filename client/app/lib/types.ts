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

export type Position = {
  fighterId: number;
  fighterName: string;
  photo: string | null;
  quantity: number;
  reservedQuantity: number;
  averagePrice: number;
  lastPrice: number;
  marketValue: number;
  unrealizedPnl: number;
};

export type UserTrade = {
  fighterId: number;
  fighterName: string;
  side: "BUY" | "SELL";
  price: number;
  quantity: number;
  executedAt: string;
};

export type ProfileData = {
  username: string;
  realizedPnl: number;
  unrealizedPnl: number;
  holdingsValue: number;
  nextPayoutAt: string | null;
  payoutAmount: number;
  holdings: Position[];
  trades: UserTrade[];
  orders: Order[];
};

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
