import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * Remaps the obfuscated vanilla Minecraft jar to Mojang official mapping names,
 * so the mod can be compiled with plain javac (no Gradle / network required).
 *
 * Usage: java RemapTool <client_mappings.txt> <obf-client.jar> <out.jar>
 */
public final class RemapTool {

    private static final Map<String, String> OBF_TO_MOJ = new HashMap<>();
    private static final Map<String, String> MOJ_TO_OBF = new HashMap<>();
    private static final Map<String, String> METHODS = new HashMap<>();
    private static final Map<String, String> FIELDS = new HashMap<>();
    private static final Map<String, String> METHOD_FALLBACK = new HashMap<>();
    private static final Map<String, String> FIELD_FALLBACK = new HashMap<>();
    private static final Set<String> AMBIGUOUS = new java.util.HashSet<>();

    private static final Set<String> MODIFIERS = Set.of(
            "public", "protected", "private", "static", "final", "abstract",
            "native", "synchronized", "transient", "volatile", "strictfp", "default"
    );

    public static void main(String[] args) throws Exception {
        Path mappings = Paths.get(args[0]);
        Path input = Paths.get(args[1]);
        Path output = Paths.get(args[2]);

        parseMappings(mappings);
        System.out.println("mappings: classes=" + OBF_TO_MOJ.size()
                + " methods=" + METHODS.size() + " fields=" + FIELDS.size());

        Remapper remapper = new Remapper() {
            @Override
            public String map(String internalName) {
                return OBF_TO_MOJ.getOrDefault(internalName, internalName);
            }

            @Override
            public String mapMethodName(String owner, String name, String descriptor) {
                String mapped = METHODS.get(owner + '\u0000' + name + '\u0000' + descriptor);
                if (mapped != null) {
                    return mapped;
                }
                mapped = METHOD_FALLBACK.get(owner + '\u0000' + name);
                return mapped != null ? mapped : name;
            }

            @Override
            public String mapFieldName(String owner, String name, String descriptor) {
                String mapped = FIELDS.get(owner + '\u0000' + name + '\u0000' + descriptor);
                if (mapped != null) {
                    return mapped;
                }
                mapped = FIELDS.get(owner + '\u0000' + name + '\u0000');
                if (mapped != null) {
                    return mapped;
                }
                mapped = FIELD_FALLBACK.get(owner + '\u0000' + name);
                return mapped != null ? mapped : name;
            }
        };

        int count = 0;
        try (JarFile jar = new JarFile(input.toFile());
             OutputStream fileOut = Files.newOutputStream(output);
             JarOutputStream out = new JarOutputStream(fileOut)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(".class") || name.startsWith("META-INF/")) {
                    continue;
                }
                try (InputStream in = jar.getInputStream(entry)) {
                    ClassReader reader = new ClassReader(in);
                    ClassWriter writer = new ClassWriter(0);
                    reader.accept(new ClassRemapper(writer, remapper), 0);
                    String newName = remapper.map(name.substring(0, name.length() - 6)) + ".class";
                    JarEntry outEntry = new JarEntry(newName);
                    outEntry.setTime(0L);
                    out.putNextEntry(outEntry);
                    out.write(writer.toByteArray());
                    out.closeEntry();
                    count++;
                }
            }
        }
        System.out.println("remapped " + count + " classes -> " + output.toAbsolutePath()
                + " (" + Files.size(output) / 1024 / 1024 + " MB)");
    }

    private static void parseMappings(Path mappings) throws Exception {
        List<String> lines = Files.readAllLines(mappings, StandardCharsets.UTF_8);

        // pass 1: class names (needed before descriptors can be computed)
        String currentMoj = null;
        List<String[]> members = new ArrayList<>();
        for (String raw : lines) {
            if (raw.isEmpty() || raw.charAt(0) == '#') {
                continue;
            }
            if (!raw.startsWith("    ")) {
                int arrow = raw.indexOf(" -> ");
                if (arrow < 0) {
                    continue;
                }
                String moj = raw.substring(0, arrow).trim().replace('.', '/');
                String obf = raw.substring(arrow + 4).trim();
                if (obf.endsWith(":")) {
                    obf = obf.substring(0, obf.length() - 1);
                }
                OBF_TO_MOJ.put(obf, moj);
                MOJ_TO_OBF.put(moj, obf);
                currentMoj = moj;
            } else if (currentMoj != null) {
                members.add(new String[]{MOJ_TO_OBF.get(currentMoj), raw.trim()});
            }
        }

        // pass 2: members
        for (String[] member : members) {
            String obfOwner = member[0];
            String body = member[1];
            int arrow = body.indexOf(" -> ");
            if (arrow < 0) {
                continue;
            }
            String left = body.substring(0, arrow).trim().replaceFirst("^\\d+:\\d+:", "").trim();
            String obfName = body.substring(arrow + 4).trim();
            left = stripModifiers(left);

            Map<String, String> typeVars = new LinkedHashMap<>();
            if (left.startsWith("<")) {
                int close = matchingBracket(left, 0);
                if (close > 0) {
                    parseTypeVars(left.substring(1, close), typeVars);
                    left = left.substring(close + 1).trim();
                }
            }

            int paren = left.indexOf('(');
            if (paren >= 0) {
                int close = left.lastIndexOf(')');
                if (close < paren) {
                    continue;
                }
                String before = left.substring(0, paren).trim();
                String argList = left.substring(paren + 1, close).trim();
                int space = before.lastIndexOf(' ');
                String name = space < 0 ? before : before.substring(space + 1).trim();
                String retType = space < 0 ? "void" : before.substring(0, space).trim();
                if (name.isEmpty() || name.equals("<init>") || name.equals("<clinit>")) {
                    continue;
                }
                String desc = "(" + argsToDesc(argList, typeVars) + ")" + typeToDesc(retType, typeVars);
                METHODS.put(obfOwner + '\u0000' + obfName + '\u0000' + desc, name);
                fallback(METHOD_FALLBACK, obfOwner + '\u0000' + obfName, name);
            } else {
                int space = left.lastIndexOf(' ');
                if (space < 0) {
                    continue;
                }
                String type = left.substring(0, space).trim();
                String name = left.substring(space + 1).trim();
                String desc = typeToDesc(type, typeVars);
                FIELDS.put(obfOwner + '\u0000' + obfName + '\u0000' + desc, name);
                FIELDS.put(obfOwner + '\u0000' + obfName + '\u0000', name);
                fallback(FIELD_FALLBACK, obfOwner + '\u0000' + obfName, name);
            }
        }
    }

    private static void fallback(Map<String, String> map, String key, String value) {
        String existing = map.get(key);
        if (existing == null) {
            map.put(key, value);
        } else if (!existing.equals(value)) {
            AMBIGUOUS.add(key);
            map.remove(key);
        }
    }

    private static String stripModifiers(String s) {
        String rest = s;
        boolean changed = true;
        while (changed) {
            changed = false;
            int space = rest.indexOf(' ');
            if (space < 0) {
                break;
            }
            String first = rest.substring(0, space);
            if (MODIFIERS.contains(first)) {
                rest = rest.substring(space + 1).trim();
                changed = true;
            }
        }
        return rest;
    }

    private static int matchingBracket(String s, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static void parseTypeVars(String body, Map<String, String> typeVars) {
        for (String part : splitTopLevel(body)) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            String name = p;
            String bound = "java.lang.Object";
            int ext = p.indexOf(" extends ");
            if (ext > 0) {
                name = p.substring(0, ext).trim();
                String b = p.substring(ext + 9).trim();
                int amp = b.indexOf('&');
                if (amp > 0) {
                    b = b.substring(0, amp).trim();
                }
                b = stripGenerics(b);
                if (!b.isEmpty()) {
                    bound = b;
                }
            }
            typeVars.put(name, bound);
        }
    }

    private static List<String> splitTopLevel(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(s.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(s.substring(start));
        return parts;
    }

    private static String argsToDesc(String args, Map<String, String> typeVars) {
        if (args.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String part : splitTopLevel(args)) {
            sb.append(typeToDesc(part.trim(), typeVars));
        }
        return sb.toString();
    }

    private static String stripGenerics(String type) {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < type.length(); i++) {
            char c = type.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (depth == 0) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String typeToDesc(String type, Map<String, String> typeVars) {
        String t = stripGenerics(type.trim());
        int arrays = 0;
        while (t.endsWith("[]")) {
            arrays++;
            t = t.substring(0, t.length() - 2).trim();
        }
        String resolved = typeVars.getOrDefault(t, t);
        String base = switch (resolved) {
            case "void" -> "V";
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "char" -> "C";
            case "short" -> "S";
            case "int" -> "I";
            case "long" -> "J";
            case "float" -> "F";
            case "double" -> "D";
            default -> {
                String internal = resolved.replace('.', '/');
                String obf = MOJ_TO_OBF.get(internal);
                yield "L" + (obf != null ? obf : internal) + ";";
            }
        };
        if (arrays == 0) {
            return base;
        }
        return "[".repeat(arrays) + base;
    }
}
