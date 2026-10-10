package ru.serega6531.packmate.service;

import lombok.SneakyThrows;
import org.springframework.security.crypto.codec.Hex;
import ru.serega6531.packmate.model.FoundPattern;
import ru.serega6531.packmate.model.Pattern;
import ru.serega6531.packmate.model.Packet;
import ru.serega6531.packmate.model.enums.PatternSearchTarget;
import ru.serega6531.packmate.model.enums.PatternSearchType;
import ru.serega6531.packmate.utils.KMPByteSearcher;
import ru.serega6531.packmate.utils.KMPStringSearcher;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Matcher;

public class PatternMatcher {

    private static final Map<String, java.util.regex.Pattern> compiledRegexes = new HashMap<>();
    private static final Map<String, KMPStringSearcher> compiledStringKmps = new HashMap<>();
    private static final Map<String, KMPByteSearcher> compiledByteKmps = new HashMap<>();

    private final byte[] contentBytes;
    private final String content;
    private final List<Pattern> patterns;
    private final String sourceIp;
    private final String destinationIp;

    private final Set<FoundPattern> result = new HashSet<>();

    public PatternMatcher(byte[] contentBytes, List<Pattern> patterns) {
        this(contentBytes, null, null, patterns);
    }

    public PatternMatcher(Packet packet, List<Pattern> patterns) {
        this(packet.getContent(), packet.getSourceIp(), packet.getDestinationIp(), patterns);
    }

    private PatternMatcher(byte[] contentBytes, String sourceIp, String destinationIp, List<Pattern> patterns) {
        this.contentBytes = contentBytes;
        this.content = new String(contentBytes);
        this.patterns = patterns;
        this.sourceIp = sourceIp;
        this.destinationIp = destinationIp;
    }

    public Set<FoundPattern> findMatches() {
        patterns.forEach(this::match);
        return result;
    }

    private void match(Pattern pattern) {
        if (pattern.getSearchTarget() != PatternSearchTarget.CONTENT) {
            matchIpAddresses(pattern);
            return;
        }
        switch (pattern.getSearchType()) {
            case REGEX -> matchRegex(pattern);
            case SUBSTRING -> matchSubstring(pattern);
            case SUBBYTES -> matchSubbytes(pattern);
        }
    }

    private void matchIpAddresses(Pattern pattern) {
        requireIpRegex(pattern);
        if (pattern.getSearchTarget() != PatternSearchTarget.DESTINATION_IP) {
            matchIp(pattern, sourceIp, PatternSearchTarget.SOURCE_IP);
        }
        if (pattern.getSearchTarget() != PatternSearchTarget.SOURCE_IP) {
            matchIp(pattern, destinationIp, PatternSearchTarget.DESTINATION_IP);
        }
    }

    private void matchIp(Pattern pattern, String address, PatternSearchTarget target) {
        if (address != null && !address.isEmpty() && compileRegex(pattern).matcher(address).find()) {
            // An address match marks the endpoint, never a range in the payload.
            result.add(FoundPattern.builder()
                    .patternId(pattern.getId())
                    .searchTarget(target)
                    .startPosition(0)
                    .endPosition(address.length() - 1)
                    .build());
        }
    }

    private static void requireIpRegex(Pattern pattern) {
        if (pattern.getSearchTarget() != PatternSearchTarget.CONTENT && pattern.getSearchType() != PatternSearchType.REGEX) {
            throw new IllegalArgumentException("IP address patterns must use regular expressions");
        }
    }

    private void matchRegex(Pattern pattern) {
        final var regex = compileRegex(pattern);
        final Matcher matcher = regex.matcher(content);
        int startPos = 0;

        while (matcher.find(startPos)) {
            addIfPossible(FoundPattern.builder()
                    .patternId(pattern.getId())
                    .startPosition(matcher.start())
                    .endPosition(matcher.end() - 1)
                    .build());
            startPos = matcher.end();
        }
    }

    private void matchSubstring(Pattern pattern) {
        final String value = pattern.getValue();
        KMPStringSearcher searcher = compileStringKMP(pattern);
        StringReader reader = new StringReader(content);

        while (true) {
            int end = searcher.search(reader) - 1;

            if (end < 0) {
                searcher.reset();
                return;
            }

            int start = end - value.length() + 1;
            addIfPossible(FoundPattern.builder()
                    .patternId(pattern.getId())
                    .startPosition(start)
                    .endPosition(end)
                    .build());
        }
    }

    @SneakyThrows
    private void matchSubbytes(Pattern pattern) {
        final byte[] value = Hex.decode(pattern.getValue());
        KMPByteSearcher searcher = compileByteKMP(pattern);
        InputStream is = new ByteArrayInputStream(contentBytes);

        while (true) {
            int end = searcher.search(is) - 1;

            if (end < 0) {
                searcher.reset();
                return;
            }

            int start = end - value.length + 1;
            addIfPossible(FoundPattern.builder()
                    .patternId(pattern.getId())
                    .startPosition(start)
                    .endPosition(end)
                    .build());
        }
    }

    private void addIfPossible(FoundPattern found) {
        if (result.stream().filter(match -> match.getSearchTarget() == found.getSearchTarget()).noneMatch(match ->
                between(match.getStartPosition(), match.getEndPosition(), found.getStartPosition()) ||
                        between(match.getStartPosition(), match.getEndPosition(), found.getEndPosition()))) {
            result.add(found);
        }
    }

    private boolean between(int a, int b, int x) {
        return a <= x && x <= b;
    }

    static void compilePattern(Pattern pattern) {
        requireIpRegex(pattern);
        switch (pattern.getSearchType()) {
            case REGEX -> compileRegex(pattern);
            case SUBSTRING -> compileStringKMP(pattern);
            case SUBBYTES -> compileByteKMP(pattern);
        }
    }

    private static java.util.regex.Pattern compileRegex(Pattern pattern) {
        return compiledRegexes.computeIfAbsent(pattern.getValue(), java.util.regex.Pattern::compile);
    }

    private static KMPStringSearcher compileStringKMP(Pattern pattern) {
        return compiledStringKmps.computeIfAbsent(pattern.getValue(), val -> new KMPStringSearcher(val.toCharArray()));
    }

    private static KMPByteSearcher compileByteKMP(Pattern pattern) {
        return compiledByteKmps.computeIfAbsent(pattern.getValue(), val -> new KMPByteSearcher(Hex.decode(val)));
    }

}
