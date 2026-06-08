import VerifyForm from "../components/VerifyForm";

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
