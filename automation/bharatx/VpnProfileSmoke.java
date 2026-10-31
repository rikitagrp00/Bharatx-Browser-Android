/* SPDX-License-Identifier: MPL-2.0 */
import com.nitaistudio.rikivpn.models.OvpnProfile;
import com.nitaistudio.rikivpn.parser.OvpnParser;
import java.nio.file.Files;
import java.nio.file.Path;

/** Uses the supplied AAR's actual JVM parser. Never starts a VPN or prints certificate/key data. */
public class VpnProfileSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Pass the bundled .ovpn files");
        int checks = 0;
        for (String argument : args) {
            String config = Files.readString(Path.of(argument));
            OvpnProfile free = OvpnParser.parse("supplied-free", config);
            if (free.getRemotes().isEmpty() || free.getCa() == null || free.getCert() == null || free.getKey() == null)
                throw new AssertionError("Supplied profile lacks endpoint or inline certificates");
            if (free.getRequiresCredentials() || free.getEmbeddedCredentials() != null)
                throw new AssertionError("Supplied profile is not an account-authenticated premium profile");
            OvpnProfile authenticated = OvpnParser.parse("provider-login-contract", config + "\nauth-user-pass\n");
            if (!authenticated.getRequiresCredentials() || authenticated.getEmbeddedCredentials() != null)
                throw new AssertionError("Provider-login detection failed");
            rejects(config.replaceAll("(?s)<ca>.*?</ca>", "ca ca.crt"));
            rejects(config.replaceAll("(?m)^remote .*\\R?", ""));
            checks += 4;
        }
        System.out.println("RikiVpn actual parser checks passed: " + checks + "; no tunnel attempted");
    }

    private static void rejects(String config) throws Exception {
        try {
            OvpnParser.parse("invalid-contract", config);
        } catch (com.nitaistudio.rikivpn.parser.OvpnParseException expected) {
            return;
        }
        throw new AssertionError("Expected SDK parser to reject invalid profile");
    }
}
