"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import {
  AreaSeries,
  ColorType,
  CrosshairMode,
  PriceScaleMode,
  createChart,
  type IChartApi,
  type ISeriesApi,
  type UTCTimestamp,
} from "lightweight-charts";
import type { Trade } from "../lib/types";

type Point = { time: UTCTimestamp; value: number };
type Tip = { left: number; top: number; value: number; time: number };

/**
 * Interactive price chart (lightweight-charts): logarithmic price axis so the
 * whole history stays visible, a time x-axis, and a crosshair tooltip on hover.
 */
export default function PriceChart({ trades }: { trades: Trade[] }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const seriesRef = useRef<ISeriesApi<"Area"> | null>(null);
  const [tip, setTip] = useState<Tip | null>(null);

  // newest-first -> ascending, deduped per second (lightweight-charts needs
  // strictly increasing, unique timestamps). Prices are sub-units -> coins.
  const points = useMemo<Point[]>(() => {
    const byTime = new Map<number, number>();
    for (const t of trades) {
      const sec = Math.floor(Date.parse(t.executedAt) / 1000);
      if (Number.isFinite(sec)) byTime.set(sec, t.price / 100);
    }
    return [...byTime.entries()]
      .sort((a, b) => a[0] - b[0])
      .map(([time, value]) => ({ time: time as UTCTimestamp, value }));
  }, [trades]);

  const stats = useMemo(() => {
    if (points.length === 0) return null;
    const values = points.map((p) => p.value);
    const first = values[0];
    const last = values[values.length - 1];
    return {
      last,
      changePct: first !== 0 ? ((last - first) / first) * 100 : 0,
      high: Math.max(...values),
      low: Math.min(...values),
    };
  }, [points]);

  // Create the chart once.
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const chart = createChart(container, {
      autoSize: true,
      layout: {
        background: { type: ColorType.Solid, color: "transparent" },
        textColor: "#71717a",
        attributionLogo: false,
      },
      rightPriceScale: {
        mode: PriceScaleMode.Logarithmic,
        borderVisible: false,
      },
      timeScale: {
        timeVisible: true,
        secondsVisible: false,
        borderVisible: false,
      },
      grid: {
        horzLines: { color: "rgba(113,113,122,0.12)" },
        vertLines: { color: "rgba(113,113,122,0.12)" },
      },
      crosshair: { mode: CrosshairMode.Magnet },
    });

    const series = chart.addSeries(AreaSeries, {
      lineColor: "#16a34a",
      topColor: "rgba(22,163,74,0.35)",
      bottomColor: "rgba(22,163,74,0.0)",
      lineWidth: 2,
      priceFormat: { type: "price", precision: 2, minMove: 0.01 },
    });

    chart.subscribeCrosshairMove((param) => {
      if (!param.point || param.time === undefined) {
        setTip(null);
        return;
      }
      const data = param.seriesData.get(series) as
        | { value?: number }
        | undefined;
      if (!data || data.value === undefined) {
        setTip(null);
        return;
      }
      // Clamp here (an event handler) so render never reads the DOM ref.
      const width = container.clientWidth;
      setTip({
        left: Math.min(param.point.x + 12, width - 130),
        top: Math.max(param.point.y - 44, 4),
        value: data.value,
        time: Number(param.time),
      });
    });

    chartRef.current = chart;
    seriesRef.current = series;
    return () => {
      chart.remove();
      chartRef.current = null;
      seriesRef.current = null;
    };
  }, []);

  // Push data whenever it changes (incl. live refresh after a trade).
  useEffect(() => {
    const series = seriesRef.current;
    const chart = chartRef.current;
    if (!series || !chart) return;
    series.setData(points);
    chart.timeScale().fitContent();
  }, [points]);

  if (points.length === 0) {
    return (
      <div className="flex h-64 items-center justify-center rounded-lg border border-dashed border-zinc-300 text-sm text-zinc-500 dark:border-zinc-700">
        No trades yet — the chart appears after the first trade.
      </div>
    );
  }

  return (
    <div>
      {stats && (
        <div className="mb-2 flex flex-wrap items-baseline gap-x-4 gap-y-1 text-sm">
          <span className="font-mono text-lg">{stats.last.toFixed(2)}</span>
          <span
            className={
              stats.changePct >= 0
                ? "text-green-600 dark:text-green-400"
                : "text-red-600 dark:text-red-400"
            }
          >
            {stats.changePct >= 0 ? "▲" : "▼"}{" "}
            {Math.abs(stats.changePct).toFixed(2)}%
          </span>
          <span className="text-xs text-zinc-500">
            H {stats.high.toFixed(2)} · L {stats.low.toFixed(2)} · log scale
          </span>
        </div>
      )}
      <div className="relative">
        <div ref={containerRef} className="h-64 w-full" />
        {tip && (
          <div
            className="pointer-events-none absolute z-10 rounded-md border border-zinc-200 bg-white px-2 py-1 text-xs shadow dark:border-zinc-700 dark:bg-zinc-900"
            style={{ left: tip.left, top: tip.top }}
          >
            <div className="font-mono">{tip.value.toFixed(2)} coins</div>
            <div className="text-zinc-500">
              {new Date(tip.time * 1000).toLocaleString()}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
