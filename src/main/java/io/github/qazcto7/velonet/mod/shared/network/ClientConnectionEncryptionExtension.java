package io.github.qazcto7.velonet.mod.shared.network;

import java.security.GeneralSecurityException;
import javax.crypto.SecretKey;

public interface ClientConnectionEncryptionExtension {
    void setupEncryption(SecretKey key) throws GeneralSecurityException;
}
