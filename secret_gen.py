import secrets
import string

def generate_strong_static_token(length=64):
    alphabet = string.ascii_letters + string.digits
    token = ''.join(secrets.choice(alphabet) for i in range(length))
    return token

if __name__ == "__main__":
    print("\n")
    for i in range(0, 5):
        token = generate_strong_static_token()
        print(f"Generated Token: {token}\n")
