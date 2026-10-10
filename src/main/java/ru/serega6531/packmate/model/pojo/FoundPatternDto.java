package ru.serega6531.packmate.model.pojo;

import lombok.Data;
import ru.serega6531.packmate.model.enums.PatternSearchTarget;

@Data
public class FoundPatternDto {

    private int patternId;
    private PatternSearchTarget searchTarget;
    private int startPosition;
    private int endPosition;

}
