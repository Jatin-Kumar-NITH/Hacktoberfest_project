"""One-time setup for emailing OTP codes with Gmail.

Run:  python setup_email.py

You need a Gmail App Password (not your normal password):
  1. Turn on 2-Step Verification: https://myaccount.google.com/security
  2. Create an App Password:      https://myaccount.google.com/apppasswords
"""
import getpass
import re
import smtplib
from email.message import EmailMessage
from pathlib import Path

ENV = Path(__file__).resolve().parent / ".env"


def set_env(values):
    lines = ENV.read_text(encoding="utf-8").splitlines() if ENV.exists() else []
    for key, value in values.items():
        for i, line in enumerate(lines):
            if re.match(rf"^\s*{key}\s*=", line):
                lines[i] = f"{key}={value}"
                break
        else:
            lines.append(f"{key}={value}")
    ENV.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    print("CampusMate · email OTP setup (Gmail)\n")
    print("You need a Gmail App Password: https://myaccount.google.com/apppasswords")
    print("(2-Step Verification must be on: https://myaccount.google.com/security)\n")
    user = input("Gmail address that will SEND the codes: ").strip()
    if not re.match(r"^[^@\s]+@[^@\s]+\.[A-Za-z]{2,}$", user):
        print("That doesn't look like an email address."); return
    pw = getpass.getpass("16-letter App Password (typing is hidden): ").replace(" ", "").strip()
    if len(pw) != 16:
        print(f"App Passwords are 16 letters; you entered {len(pw)}. Please copy it again."); return
    to = input(f"Send a test email to [{user}]: ").strip() or user

    msg = EmailMessage()
    msg["Subject"] = "CampusMate: email OTP is working"
    msg["From"] = user
    msg["To"] = to
    msg.set_content("This is a test from CampusMate AI. Login and sign-up codes will now arrive by email.")

    port_used, last_error = None, None
    for port in (587, 465):
        print(f"\nConnecting to Gmail on port {port}…")
        try:
            if port == 465:
                server = smtplib.SMTP_SSL("smtp.gmail.com", 465, timeout=15)
            else:
                server = smtplib.SMTP("smtp.gmail.com", 587, timeout=15)
                server.starttls()
            with server:
                server.login(user, pw)
                server.send_message(msg)
            port_used = port
            break
        except smtplib.SMTPAuthenticationError:
            print("\n✗ Gmail rejected the login. Use an App Password (not your normal password)"
                  " and make sure 2-Step Verification is on."); return
        except Exception as e:
            last_error = e
            print(f"  port {port} did not work: {e}")
    if not port_used:
        print(f"\n✗ Could not reach Gmail on port 587 or 465 ({last_error}).")
        print("  Your network is blocking email. Try a mobile hotspot, or pause antivirus 'email shield' / firewall.")
        print("  Until then, OTP codes keep appearing in the server terminal, which is fine for a demo."); return

    set_env({"SMTP_HOST": "smtp.gmail.com", "SMTP_PORT": str(port_used), "SMTP_USER": user, "SMTP_PASSWORD": pw, "SMTP_FROM": f"CampusMate <{user}>"})
    print(f"\n✓ Test email sent to {to} (port {port_used}). Settings saved to {ENV.name}.")
    print("  Restart the server (Ctrl + C, then python app.py) and OTP codes will be emailed.")


if __name__ == "__main__":
    main()
