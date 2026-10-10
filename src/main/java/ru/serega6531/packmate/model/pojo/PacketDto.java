package ru.serega6531.packmate.model.pojo;

import lombok.Data;

import java.util.Set;

@Data
public class PacketDto {

    private Long id;
    private Set<FoundPatternDto> matches;
    private long timestamp;
    private String sourceIp;
    private String destinationIp;
    private boolean incoming;
    private boolean ungzipped;
    private boolean webSocketParsed;
    private boolean tlsDecrypted;
    private boolean hasHttpBody;
    private byte[] content;

}
