"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import {
  AreaSeries,
  ColorType,
  CrosshairMode,
  createChart,
  type IChartApi,
  type ISeriesApi,
  type UTCTimestamp,
} from "lightweight-charts";
import type { PortfolioSnapshot } from "../lib/types";

type Metric = "holdings" | "pnl";
type Point = { time: UTCTimestamp; value: number };
type Tip = { left: number; top: number; value: number; time: number };

const METRICS: { key: Metric; label: string; color: string; fill: string }[] = [
  { key: "holdings", label: "Holdings value", color: "#16a34a", fill: "22,163,74" },
  { key: "pnl", label: "Total P&L", color: "#6366f1", fill: "99,102,241" },
];

/** Snapshot value (sub-units) for the chosen metric, converted to coins. */
function valueOf(s: PortfolioSnapshot, metric: Metric): number {
  const subunits =
    metric === "holdings"
      ? s.holdingsValue
      : s.realizedPnl + s.unrealizedPnl;
  return subunits / 100;
}

/**
 * Portfolio performance over time from hourly snapshots: toggle between holdings
 * value and total P&L. Mirrors the fighter PriceChart (lightweight-charts) for a
 * consistent look — crosshair tooltip, auto-fit, transparent background.
 */
export default function PortfolioChart({
  data,
}: {
  data: PortfolioSnapshot[];
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const seriesRef = useRef<ISeriesApi<"Area"> | null>(null);
  const [metric, setMetric] = useState<Metric>("holdings");
  const [tip, setTip] = useState<Tip | null>(null);

  const active = METRICS.find((m) => m.key === metric)!;

  // Dedupe per second (lightweight-charts needs strictly-increasing, unique
  // timestamps) keeping the latest value, then sort ascending.
  const points = useMemo<Point[]>(() => {
    const byTime = new Map<number, number>();
    for (const s of data) {
      const sec = Math.floor(Date.parse(s.capturedAt) / 1000);
      if (Number.isFinite(sec)) byTime.set(sec, valueOf(s, metric));
    }
    return [...byTime.entries()]
      .sort((a, b) => a[0] - b[0])
      .map(([time, value]) => ({ time: time as UTCTimestamp, value }));
  }, [data, metric]);

  const latest = points.length ? points[points.length - 1].value : null;

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
      rightPriceScale: { borderVisible: false },
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
      lineWidth: 2,
      priceFormat: { type: "price", precision: 2, minMove: 0.01 },
    });

    chart.subscribeCrosshairMove((param) => {
      if (!param.point || param.time === undefined) {
        setTip(null);
        return;
      }
      const d = param.seriesData.get(series) as { value?: number } | undefined;
      if (!d || d.value === undefined) {
        setTip(null);
        return;
      }
      const width = container.clientWidth;
      setTip({
        left: Math.min(param.point.x + 12, width - 130),
        top: Math.max(param.point.y - 44, 4),
        value: d.value,
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

  // Recolor + push data whenever the metric or data changes.
  useEffect(() => {
    const series = seriesRef.current;
    const chart = chartRef.current;
    if (!series || !chart) return;
    series.applyOptions({
      lineColor: active.color,
      topColor: `rgba(${active.fill},0.35)`,
      bottomColor: `rgba(${active.fill},0.0)`,
    });
    series.setData(points);
    chart.timeScale().fitContent();
  }, [points, active]);

  return (
    <div>
      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
        <div className="inline-flex rounded-lg border border-zinc-200 p-0.5 text-xs dark:border-zinc-800">
          {METRICS.map((m) => (
            <button
              key={m.key}
              type="button"
              onClick={() => setMetric(m.key)}
              className={`rounded-md px-2.5 py-1 font-medium transition ${
                metric === m.key
                  ? "bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900"
                  : "text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100"
              }`}
            >
              {m.label}
            </button>
          ))}
        </div>
        {latest !== null && (
          <span className="font-mono text-lg" style={{ color: active.color }}>
            {latest.toLocaleString(undefined, {
              minimumFractionDigits: 2,
              maximumFractionDigits: 2,
            })}
          </span>
        )}
      </div>
      <div className="relative">
        <div ref={containerRef} className="h-56 w-full" />
        {points.length === 0 && (
          <div className="absolute inset-0 flex items-center justify-center rounded-lg border border-dashed border-zinc-300 px-4 text-center text-sm text-zinc-500 dark:border-zinc-700">
            No history yet — a snapshot is recorded every hour.
          </div>
        )}
        {tip && (
          <div
            className="pointer-events-none absolute z-10 rounded-md border border-zinc-200 bg-white px-2 py-1 text-xs shadow dark:border-zinc-700 dark:bg-zinc-900"
            style={{ left: tip.left, top: tip.top }}
          >
            <div className="font-mono">
              {tip.value.toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2,
              })}{" "}
              coins
            </div>
            <div className="text-zinc-500">
              {new Date(tip.time * 1000).toLocaleString()}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
