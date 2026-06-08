import type { Metadata } from "next";
import VerifyForm from "../components/VerifyForm";

export const metadata: Metadata = {
  title: "Verify email",
  robots: { index: false, follow: false },
};

export default async function VerifyPage({
  searchParams,
}: {
  searchParams: Promise<{ token?: string }>;
}) {
  const { token } = await searchParams;
  return (
    <div className="flex flex-1 flex-col items-center px-6">
      <VerifyForm token={token ?? ""} />
    </div>
  );
}
