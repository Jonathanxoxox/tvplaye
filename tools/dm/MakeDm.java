package androidx.profileinstaller;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CI helper: turns the baseline profile that ships in the APK (format 010) into the ART profile format that
 * Android 12+ reads (015), keyed to the given APK name, for the dex metadata (.dm) next to the release APK.
 *
 * It sits in the library's package because the transcoder the runtime profile installer uses is package-private;
 * it is compiled against the profileinstaller jar of the build and never ships in the app.
 *
 * Usage: MakeDm baseline.prof baseline.profm base.apk out.prof   (the .profm may be missing)
 */
public class MakeDm {
    public static void main(String[] args) throws Exception {
        Path prof = Paths.get(args[0]);
        Path profm = Paths.get(args[1]);
        String apkName = args[2];
        Path out = Paths.get(args[3]);

        InputStream is = new ByteArrayInputStream(Files.readAllBytes(prof));
        byte[] version = ProfileTranscoder.readHeader(is, ProfileTranscoder.MAGIC_PROF);
        DexProfileData[] data = ProfileTranscoder.readProfile(is, version, apkName);
        if (Files.exists(profm)) {
            InputStream meta = new ByteArrayInputStream(Files.readAllBytes(profm));
            byte[] metaVersion = ProfileTranscoder.readHeader(meta, ProfileTranscoder.MAGIC_PROFM);
            data = ProfileTranscoder.readMeta(meta, metaVersion, ProfileVersion.V015_S, data);
        }
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ProfileTranscoder.writeHeader(os, ProfileVersion.V015_S);
        if (!ProfileTranscoder.transcodeAndWriteBody(os, ProfileVersion.V015_S, data)) {
            throw new IllegalStateException("profile format 015 refused");
        }
        Files.write(out, os.toByteArray());
        System.out.println("dex files: " + data.length + ", wrote " + os.size() + " bytes");
    }
}
