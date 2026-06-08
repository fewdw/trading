import ResetPasswordForm from "../components/ResetPasswordForm";

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
