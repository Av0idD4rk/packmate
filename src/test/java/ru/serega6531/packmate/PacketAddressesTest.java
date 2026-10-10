package ru.serega6531.packmate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.serega6531.packmate.configuration.ApplicationConfiguration;
import ru.serega6531.packmate.model.Packet;
import ru.serega6531.packmate.model.pojo.PacketDto;
import ru.serega6531.packmate.service.optimization.PacketsMerger;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PacketAddressesTest {

    @ParameterizedTest
    @CsvSource({
            "192.0.2.1, 192.0.2.2, true",
            "192.0.2.2, 192.0.2.1, false",
            "2001:db8::1, 2001:db8::2, true",
            "2001:db8::2, 2001:db8::1, false"
    })
    void addressesSurviveMergingAndDtoMapping(String source, String destination, boolean incoming) {
        Packet first = Packet.builder()
                .sourceIp(source)
                .destinationIp(destination)
                .incoming(incoming)
                .content(new byte[] {1})
                .build();
        Packet second = first.toBuilder().content(new byte[] {2}).build();
        List<Packet> packets = new ArrayList<>(List.of(first, second));

        new PacketsMerger().mergeAdjacentPackets(packets);

        assertEquals(1, packets.size());
        PacketDto dto = new ApplicationConfiguration().modelMapper().map(packets.get(0), PacketDto.class);
        assertEquals(source, dto.getSourceIp());
        assertEquals(destination, dto.getDestinationIp());
        assertEquals(incoming, dto.isIncoming());
        assertArrayEquals(new byte[] {1, 2}, dto.getContent());
    }

    @Test
    void legacyPacketsWithoutAddressesStillMap() {
        Packet packet = Packet.builder().content(new byte[] {1}).build();

        PacketDto dto = new ApplicationConfiguration().modelMapper().map(packet, PacketDto.class);

        assertNull(dto.getSourceIp());
        assertNull(dto.getDestinationIp());
        assertArrayEquals(packet.getContent(), dto.getContent());
    }
}
