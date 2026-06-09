// Canonical signup validation rules. These mirror the backend exactly
// (SignupDTO annotations + AuthService cross-field checks) so the client and
// server never disagree. Keep the two in sync if you change anything here.

export const USERNAME_PATTERN = /^[A-Za-z0-9_]+$/;
export const USERNAME_MIN = 3;
export const USERNAME_MAX = 30;
export const PASSWORD_MIN = 8;
export const PASSWORD_MAX = 72;

// Mirrors AuthService.RESERVED_USERNAMES (+ the treasury account name). Blocked
// to prevent impersonation/abuse.
const RESERVED_USERNAMES = new Set([
  "admin",
  "administrator",
  "root",
  "system",
  "treasury",
  "moderator",
  "support",
  "fightermarket",
  "__treasury__",
]);

// Mirrors AuthService.WEAK_PASSWORDS — the most common/guessable choices a
// length rule alone would let through.
const WEAK_PASSWORDS = new Set([
  "password",
  "password1",
  "password123",
  "12345678",
  "123456789",
  "1234567890",
  "qwertyui",
  "qwerty123",
  "11111111",
  "00000000",
  "iloveyou",
  "baseball",
  "football",
  "welcome1",
  "admin123",
  "letmein1",
  "abc12345",
  "fighter1",
]);

export function validateUsername(username: string): string | null {
  if (username.length < USERNAME_MIN || username.length > USERNAME_MAX) {
    return `Username must be ${USERNAME_MIN}-${USERNAME_MAX} characters.`;
  }
  if (!USERNAME_PATTERN.test(username)) {
    return "Username may only contain letters, numbers, and underscores.";
  }
  if (RESERVED_USERNAMES.has(username.toLowerCase())) {
    return "That username isn't available.";
  }
  return null;
}

export function validatePassword(password: string): string | null {
  if (password.length < PASSWORD_MIN || password.length > PASSWORD_MAX) {
    return `Password must be ${PASSWORD_MIN}-${PASSWORD_MAX} characters.`;
  }
  if (WEAK_PASSWORDS.has(password.toLowerCase()) || new Set(password).size === 1) {
    return "Password is too common — choose something harder to guess.";
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
