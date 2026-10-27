-- Sign-ups waiting for the emailed verification code. The account is only created once the code
-- is entered, so nobody can hold an address they don't own. Only a SHA-256 hash of the code is
-- stored; the attempts counter caps guessing.
CREATE TABLE signup_verifications (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email          VARCHAR(255) NOT NULL,
    email_key      VARCHAR(255) NOT NULL UNIQUE,
    full_name      VARCHAR(150) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    code_hash      VARCHAR(64) NOT NULL,
    attempts       INT NOT NULL DEFAULT 0,
    expires_at     TIMESTAMPTZ NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
