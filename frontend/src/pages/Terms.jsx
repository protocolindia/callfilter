import React from 'react';

export default function Terms() {
  return (
    <div className="terms-wrap">
      <div className="terms-page">
        <header className="terms-head">
          <h1>📋 Terms & Conditions</h1>
          <p className="muted">Last updated: May 2026</p>
        </header>

        <section>
          <h2>1. Acceptance of Terms</h2>
          <p>
            By creating an account in the CyberGuard AI app and using the related services
            (collectively, the "Service"), you agree to be bound by these Terms &amp;
            Conditions. If you do not agree, please do not use the Service.
          </p>
        </section>

        <section>
          <h2>2. The Service</h2>
          <p>
            CyberGuard AI is an Android application that lets you block or allow phone
            calls based on rules you define (prefix, suffix, number range, contacts-only
            mode). The app communicates with our cloud backend at
            <code> api.app.onephone.pro </code> for account management, OTP verification,
            and synchronisation of your settings.
          </p>
        </section>

        <section>
          <h2>3. Account &amp; Verification</h2>
          <p>
            To use the Service you must register with a valid mobile number. We send a
            one-time password (OTP) to verify the number is yours. You then choose a
            4-digit PIN that secures access to the app on your device. The PIN is stored
            on your phone as a SHA-256 hash; the same hash is also kept on our servers
            so you can sign in from another device in the future.
          </p>
        </section>

        <section>
          <h2>4. Address-Book Upload</h2>
          <p>
            When you grant the Contacts permission, the app uploads the entries from
            your phone's address book to our servers. We use this data to enable
            features such as contacts-only blocking, synchronisation across your
            devices, and to improve our spam-detection signals over time.
          </p>
          <p>
            Each contact may include the name, phone number(s), email address(es),
            postal address(es), organisation, website, important dates and notes you
            have stored on your device. We do not upload contacts that have no phone
            number associated.
          </p>
          <p>
            We re-sync only NEW contacts after the first upload — contacts you've
            already uploaded are not transmitted again.
          </p>
        </section>

        <section>
          <h2>5. Blocking Rules</h2>
          <p>
            Your blocking and allow rules (prefixes, suffixes, ranges, contacts-only
            mode) are kept on your device and also synchronised to our cloud so they
            remain available if you re-install the app or switch devices.
          </p>
        </section>

        <section>
          <h2>6. Data Storage &amp; Security</h2>
          <p>
            Your data is stored on managed servers (Railway) protected by industry-
            standard security controls, including TLS in transit and at-rest encryption
            on the database. We do not sell your contact information to third parties.
          </p>
        </section>

        <section>
          <h2>7. Limitations of the Service</h2>
          <p>
            Call blocking on Android relies on platform features that vary by device
            manufacturer and operating-system version. We make best efforts to support
            modern Android phones, but we cannot guarantee that every call from a
            blocked number will be silenced on every device. The Service is provided
            "as is" without warranty of any kind.
          </p>
        </section>

        <section>
          <h2>8. Acceptable Use</h2>
          <p>
            You agree not to use the Service to harass others, to impersonate any
            person, to upload contacts on behalf of someone other than yourself, or to
            attempt to compromise the integrity of our servers.
          </p>
        </section>

        <section>
          <h2>9. Changes to These Terms</h2>
          <p>
            We may update these Terms from time to time. The "Last updated" date at the
            top of this page reflects the most recent change. Continued use of the
            Service after a change constitutes acceptance of the new Terms.
          </p>
        </section>

        <section>
          <h2>10. Contact</h2>
          <p>
            Questions about these Terms? Reach us at the email address listed on
            <code> app.onephone.pro</code>.
          </p>
        </section>

        <p className="terms-footer muted">
          By tapping "Accept Terms &amp; Conditions" in the app, you confirm that you
          have read and agreed to these Terms.
        </p>
      </div>
    </div>
  );
}
