interface ErrorMessageProps {
  error: Error | null
}

/** Mostra a mensagem do erro; para ApiError ela já é o `detail` do backend. */
export function ErrorMessage({ error }: ErrorMessageProps) {
  if (!error) return null
  return (
    <p role="alert" className="error-message">
      {error.message}
    </p>
  )
}
