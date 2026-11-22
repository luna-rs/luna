# RSA Keys

This folder contains the default generated RSA keys for Luna.

The values for the _public key_ (rsapub.json) go client side. If you don't have a setting, you may have to enable RSA in your client.

The _private key_ (rsapriv.json) stays in this folder as is. The values will be automatically parsed on startup.


# Generating New Keys

Changing the default keys is **incredibly** important. If you don't, anyone downloading Luna will know your private key and will be able to sniff usernames and passwords when your players login. You can generate new keys using the [rsakeygen](https://github.com/luna-rs/rsakeygen) tool.