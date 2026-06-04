import React from 'react';

export default function Privacy() {
  return (
    <div className="terms-wrap">
      <div className="terms-page">
        <header className="terms-head">
          <h1>🔒 Privacy Policy</h1>
          <p className="muted">Last updated: May 2026</p>
        </header>

        <section>
          <h2>1. What we collect</h2>
          <p>
            CyberGuard AI collects the minimum information needed to operate the
            service: your mobile number (used as your account identifier), the
            blocking rules you create, and a log of calls blocked or attempted
            on your device. You may optionally upload your contacts so they are
            available across devices.
          </p>
        </section>

        <section>
          <h2>2. How we use it</h2>
          <p>
            Your data is used only to provide the call-filtering service —
            evaluating incoming calls against your rules, syncing your settings
            across your devices, and showing you a history of blocked calls. We
            do not sell or share your data with advertisers.
          </p>
        </section>

        <section>
          <h2>3. Block-reason categorisation</h2>
          <p>
            When you block a call, you may pick a reason ("Spam", "Phishing",
            etc.). These reasons are stored on your account and visible only to
            you and the platform administrator. They help us improve the
            service by understanding which types of unwanted calls are most
            common.
          </p>
        </section>

        <section>
          <h2>4. Contacts</h2>
          <p>
            If you enable contact sync, your contacts are uploaded to our
            server so the app can identify known callers across devices. You
            can disable this at any time in the Profile screen; doing so
            deletes the uploaded copy.
          </p>
        </section>

        <section>
          <h2>5. Payments</h2>
          <p>
            Subscription payments are processed by Razorpay. We receive a
            payment confirmation (amount, plan, order ID) but never your card
            details. Razorpay's privacy policy governs handling of payment
            instrument data.
          </p>
        </section>

        <section>
          <h2>6. Data retention &amp; deletion</h2>
          <p>
            Your data is retained as long as your account is active. You can
            request complete deletion of your account and all associated data
            by emailing <a href="mailto:support@onephone.pro">support@onephone.pro</a>.
            Deletion is permanent and irreversible.
          </p>
        </section>

        <section>
          <h2>7. Security</h2>
          <p>
            Your PIN is hashed before being stored. Communication with our
            servers is over HTTPS. We use industry-standard practices but no
            system is perfectly secure; report any concern to the support
            address above.
          </p>
        </section>

        <section>
          <h2>8. Contact</h2>
          <p>
            For questions about this policy or to exercise any rights under
            applicable data-protection law (DPDP Act in India, GDPR in EU),
            email <a href="mailto:support@onephone.pro">support@onephone.pro</a>.
          </p>
        </section>

        <footer className="terms-foot muted">
          © 2026 OnePhone. By using CyberGuard AI you consent to this Privacy Policy.
        </footer>
      </div>
    </div>
  );
}
