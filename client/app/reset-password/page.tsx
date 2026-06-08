import type { Metadata } from "next";
import ResetPasswordForm from "../components/ResetPasswordForm";

export const metadata: Metadata = {
  title: "Reset password",
  robots: { index: false, follow: false },
};

export default async function ResetPasswordPage({
  searchParams,
}: {
  searchParams: Promise<{ token?: string }>;
}) {
  const { token } = await searchParams;
  return (
    <div className="flex flex-1 flex-col items-center px-6">
      <ResetPasswordForm token={token ?? ""} />
    </div>
  );
}
