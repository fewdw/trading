// Canonical signup validation rules. These mirror the backend exactly
// (SignupDTO annotations + AuthService cross-field checks) so the client and
// server never disagree. Keep the two in sync if you change anything here.

export const USERNAME_PATTERN = /^[A-Za-z0-9_]+$/;
export const USERNAME_MIN = 3;
export const USERNAME_MAX = 30;
export const PASSWORD_MIN = 8;
export const PASSWORD_MAX = 72;

export function validateUsername(username: string): string | null {
  if (username.length < USERNAME_MIN || username.length > USERNAME_MAX) {
    return `Username must be ${USERNAME_MIN}-${USERNAME_MAX} characters.`;
  }
  if (!USERNAME_PATTERN.test(username)) {
    return "Username may only contain letters, numbers, and underscores.";
  }
  return null;
}

export function validatePassword(password: string): string | null {
  if (password.length < PASSWORD_MIN || password.length > PASSWORD_MAX) {
    return `Password must be ${PASSWORD_MIN}-${PASSWORD_MAX} characters.`;
  }
  return null;
}

/** Full signup check; returns the first error message, or null if all good. */
export function validateSignup(input: {
  username: string;
  password: string;
  confirmPassword: string;
}): string | null {
  const { username, password, confirmPassword } = input;

  if (!username || !password) return "All fields are required.";

  const usernameError = validateUsername(username);
  if (usernameError) return usernameError;

  const passwordError = validatePassword(password);
  if (passwordError) return passwordError;

  if (password.toLowerCase() === username.toLowerCase()) {
    return "Password cannot be the same as your username.";
  }
  if (password !== confirmPassword) return "Passwords do not match.";

  return null;
}
