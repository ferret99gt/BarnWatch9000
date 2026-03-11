package com.barnwatch9000.model;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public enum GridLayoutPreset
{
    SINGLE("1x1", 1, 1, uniformPlacements(1, 1)),
    QUAD("2x2", 2, 2, uniformPlacements(2, 2)),
    NINE("3x3", 3, 3, uniformPlacements(3, 3)),
    SIX_TALL("3x2", 2, 3, uniformPlacements(2, 3)),
    SIX_WIDE("2x3", 3, 2, uniformPlacements(3, 2)),
    THREE_OVER_FOUR("3 over 4", 12, 2, List.of(
            new TilePlacement(0, 0, 4, 1),
            new TilePlacement(4, 0, 4, 1),
            new TilePlacement(8, 0, 4, 1),
            new TilePlacement(0, 1, 3, 1),
            new TilePlacement(3, 1, 3, 1),
            new TilePlacement(6, 1, 3, 1),
            new TilePlacement(9, 1, 3, 1))),
    BIG_SIX("Big 6", 3, 3, List.of(
            new TilePlacement(0, 0, 2, 2),
            new TilePlacement(2, 0, 1, 1),
            new TilePlacement(2, 1, 1, 1),
            new TilePlacement(0, 2, 1, 1),
            new TilePlacement(1, 2, 1, 1),
            new TilePlacement(2, 2, 1, 1))),
    BIG_SEVEN("Big 7", 4, 3, List.of(
            new TilePlacement(0, 0, 3, 2),
            new TilePlacement(3, 0, 1, 1),
            new TilePlacement(3, 1, 1, 1),
            new TilePlacement(0, 2, 1, 1),
            new TilePlacement(1, 2, 1, 1),
            new TilePlacement(2, 2, 1, 1),
            new TilePlacement(3, 2, 1, 1))),
    BIG_EIGHT("Big 8", 4, 4, List.of(
            new TilePlacement(0, 0, 3, 3),
            new TilePlacement(3, 0, 1, 1),
            new TilePlacement(3, 1, 1, 1),
            new TilePlacement(3, 2, 1, 1),
            new TilePlacement(0, 3, 1, 1),
            new TilePlacement(1, 3, 1, 1),
            new TilePlacement(2, 3, 1, 1),
            new TilePlacement(3, 3, 1, 1)));

    private final String label;
    private final int columns;
    private final int rows;
    private final List<TilePlacement> placements;

    GridLayoutPreset(String label, int columns, int rows, List<TilePlacement> placements)
    {
        this.label = label;
        this.columns = columns;
        this.rows = rows;
        this.placements = List.copyOf(placements);
    }

    public int columns()
    {
        return columns;
    }

    public int rows()
    {
        return rows;
    }

    public int capacity()
    {
        return placements.size();
    }

    public List<TilePlacement> placements()
    {
        return placements;
    }

    public static Optional<GridLayoutPreset> fromStoredValue(String value)
    {
        if (value == null || value.isBlank())
        {
            return Optional.empty();
        }

        return Arrays.stream(values())
                .filter(layout -> layout.label.equalsIgnoreCase(value) || layout.name().equalsIgnoreCase(value))
                .findFirst();
    }

    @Override
    public String toString()
    {
        return label;
    }

    private static List<TilePlacement> uniformPlacements(int columns, int rows)
    {
        List<TilePlacement> placements = new ArrayList<>(columns * rows);
        for (int row = 0; row < rows; row++)
        {
            for (int column = 0; column < columns; column++)
            {
                placements.add(new TilePlacement(column, row, 1, 1));
            }
        }
        return placements;
    }

    public record TilePlacement(int column, int row, int columnSpan, int rowSpan)
    {
    }
}
