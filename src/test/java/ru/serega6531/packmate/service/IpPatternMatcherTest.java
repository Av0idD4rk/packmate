package ru.serega6531.packmate.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.serega6531.packmate.configuration.ApplicationConfiguration;
import ru.serega6531.packmate.model.CtfService;
import ru.serega6531.packmate.model.FoundPattern;
import ru.serega6531.packmate.model.Packet;
import ru.serega6531.packmate.model.Pattern;
import ru.serega6531.packmate.model.enums.*;
import ru.serega6531.packmate.model.pojo.FoundPatternDto;
import ru.serega6531.packmate.model.pojo.PatternCreateDto;
import ru.serega6531.packmate.model.pojo.PatternDto;
import ru.serega6531.packmate.repository.PatternRepository;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

import static org.junit.jupiter.api.Assertions.*;

class IpPatternMatcherTest {

    private Packet packet(String source, String destination, String content) {
        return Packet.builder().sourceIp(source).destinationIp(destination)
                .content(content.getBytes(StandardCharsets.UTF_8)).build();
    }

    private Pattern pattern(int id, PatternSearchTarget target, String regex) {
        Pattern pattern = new Pattern();
        pattern.setId(id);
        pattern.setSearchType(PatternSearchType.REGEX);
        pattern.setSearchTarget(target);
        pattern.setValue(regex);
        pattern.setEnabled(true);
        pattern.setActionType(PatternActionType.FIND);
        pattern.setDirectionType(PatternDirectionType.BOTH);
        return pattern;
    }

    @Test
    void sourceAndDestinationAreMatchedIndependentlyFromPayload() {
        Packet packet = packet("10.20.30.4", "192.0.2.8", "192.0.2.99");
        List<Pattern> patterns = List.of(
                pattern(1, PatternSearchTarget.SOURCE_IP, "^10\\.20\\.30\\.[0-9]+$"),
                pattern(2, PatternSearchTarget.DESTINATION_IP, "^192\\.0\\.2\\.8$"),
                pattern(3, PatternSearchTarget.SOURCE_IP, "192\\.0\\.2"),
                pattern(4, PatternSearchTarget.DESTINATION_IP, "^10\\."),
                pattern(5, PatternSearchTarget.EITHER_IP, "192\\.0\\.2\\.99"));

        Set<FoundPattern> matches = new PatternMatcher(packet, patterns).findMatches();

        assertEquals(2, matches.size());
        assertTrue(matches.stream().anyMatch(m -> m.getPatternId() == 1 && m.getSearchTarget() == PatternSearchTarget.SOURCE_IP));
        assertTrue(matches.stream().anyMatch(m -> m.getPatternId() == 2 && m.getSearchTarget() == PatternSearchTarget.DESTINATION_IP));
    }

    @Test
    void eitherIpHandlesIpv6AndKeepsMatchesForBothEndpoints() {
        Packet packet = packet("2001:db8:0:0:0:0:0:1", "2001:db8:0:0:0:0:0:2", "body");
        Set<FoundPattern> matches = new PatternMatcher(packet,
                List.of(pattern(1, PatternSearchTarget.EITHER_IP, "(?i)^2001:DB8:"))).findMatches();

        assertEquals(Set.of(PatternSearchTarget.SOURCE_IP, PatternSearchTarget.DESTINATION_IP),
                matches.stream().map(FoundPattern::getSearchTarget).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void legacyPacketsWithoutAddressesDoNotMatchEvenWildcard() {
        assertTrue(new PatternMatcher(packet(null, null, "10.20.30.4"),
                List.of(pattern(1, PatternSearchTarget.EITHER_IP, ".*"))).findMatches().isEmpty());
    }

    @Test
    void missingEndpointDoesNotPreventMatchingPresentEndpoint() {
        Set<FoundPattern> matches = new PatternMatcher(packet(null, "192.0.2.8", "body"),
                List.of(pattern(1, PatternSearchTarget.EITHER_IP, "^192\\."))).findMatches();
        assertEquals(1, matches.size());
        assertEquals(PatternSearchTarget.DESTINATION_IP, matches.iterator().next().getSearchTarget());
    }

    @Test
    void addressAndContentMatchesDoNotSuppressEachOther() {
        Packet packet = packet("10.20.30.4", "192.0.2.8", "FLAG");
        Pattern address = pattern(1, PatternSearchTarget.SOURCE_IP, "^10\\.");
        Pattern content = pattern(2, PatternSearchTarget.CONTENT, "FLAG");

        for (List<Pattern> order : List.of(List.of(address, content), List.of(content, address))) {
            Set<FoundPattern> matches = new PatternMatcher(packet, order).findMatches();
            assertEquals(2, matches.size());
            assertTrue(matches.stream().anyMatch(m -> m.getSearchTarget() == PatternSearchTarget.CONTENT && m.getEndPosition() == 3));
        }
    }

    @Test
    void emptyRegexMatchOnIpTerminatesAndMarksAddress() {
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            Set<FoundPattern> matches = new PatternMatcher(packet("10.20.30.4", null, "body"),
                    List.of(pattern(1, PatternSearchTarget.SOURCE_IP, "^"))).findMatches();
            assertEquals(1, matches.size());
        });
    }

    @ParameterizedTest
    @EnumSource(value = PatternSearchType.class, names = {"SUBSTRING", "SUBBYTES"})
    void nonRegexIpPatternsAreRejected(PatternSearchType searchType) {
        Pattern pattern = pattern(1, PatternSearchTarget.EITHER_IP, "10");
        pattern.setSearchType(searchType);
        assertThrows(IllegalArgumentException.class, () -> PatternMatcher.compilePattern(pattern));
    }

    @Test
    void invalidIpRegexIsRejectedBeforeSaving() {
        assertThrows(PatternSyntaxException.class,
                () -> PatternMatcher.compilePattern(pattern(1, PatternSearchTarget.EITHER_IP, "[")));
    }

    @Test
    void targetsRoundTripThroughDtosAndLegacyNullMeansContent() {
        var mapper = new ApplicationConfiguration().modelMapper();
        var dto = new PatternCreateDto();
        assertEquals(PatternSearchTarget.CONTENT, mapper.map(dto, Pattern.class).getSearchTarget());
        dto.setSearchTarget(PatternSearchTarget.EITHER_IP);
        Pattern pattern = mapper.map(dto, Pattern.class);
        pattern.setId(1);
        assertEquals(PatternSearchTarget.EITHER_IP, mapper.map(pattern, PatternDto.class).getSearchTarget());
        pattern.setSearchTarget(null);
        assertEquals(PatternSearchTarget.CONTENT, mapper.map(pattern, PatternDto.class).getSearchTarget());
        FoundPattern found = FoundPattern.builder().searchTarget(PatternSearchTarget.SOURCE_IP).build();
        assertEquals(PatternSearchTarget.SOURCE_IP, mapper.map(found, FoundPatternDto.class).getSearchTarget());
        assertEquals(PatternSearchTarget.CONTENT, mapper.map(FoundPattern.builder().build(), FoundPatternDto.class).getSearchTarget());
    }

    @Test
    void existingContentPatternsDoNotStartMatchingAddressMetadata() {
        Pattern pattern = pattern(1, null, "10\\.20\\.30\\.4");
        assertTrue(new PatternMatcher(packet("10.20.30.4", "192.0.2.8", "body"), List.of(pattern)).findMatches().isEmpty());
    }

    @Test
    void liveFindIgnoreDirectionAndServiceFiltersReceiveAddresses() {
        Pattern find = pattern(1, PatternSearchTarget.SOURCE_IP, "^10\\.");
        find.setServiceId(80);
        find.setDirectionType(PatternDirectionType.INPUT);
        Pattern ignore = pattern(2, PatternSearchTarget.EITHER_IP, "^192\\.");
        ignore.setActionType(PatternActionType.IGNORE);
        Pattern disabled = pattern(3, PatternSearchTarget.EITHER_IP, ".*");
        disabled.setEnabled(false);
        Pattern deleted = pattern(4, PatternSearchTarget.EITHER_IP, ".*");
        deleted.setDeleted(true);
        PatternService service = service(List.of(find, ignore, disabled, deleted));
        CtfService http = new CtfService();
        http.setPort(80);
        Packet packet = packet("10.20.30.4", "192.0.2.8", "body");

        var found = service.findMatches(packet, http, PatternDirectionType.INPUT, PatternActionType.FIND);
        assertEquals(1, found.size());
        assertEquals(1, found.iterator().next().getPatternId());
        assertTrue(service.findMatches(packet, http, PatternDirectionType.OUTPUT, PatternActionType.FIND).isEmpty());
        assertEquals(2, service.findMatches(packet, http, PatternDirectionType.INPUT, PatternActionType.IGNORE)
                .iterator().next().getPatternId());
        http.setPort(443);
        assertTrue(service.findMatches(packet, http, PatternDirectionType.INPUT, PatternActionType.FIND).isEmpty());
        assertEquals(1, service.matchOne(packet, find).size());
    }

    private PatternService service(List<Pattern> patterns) {
        PatternRepository repository = (PatternRepository) Proxy.newProxyInstance(
                PatternRepository.class.getClassLoader(), new Class<?>[] {PatternRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findAll")) return patterns;
                    throw new UnsupportedOperationException(method.getName());
                });
        PatternService service = new PatternService(repository, null, null, new ApplicationConfiguration().modelMapper());
        service.init();
        return service;
    }
}
