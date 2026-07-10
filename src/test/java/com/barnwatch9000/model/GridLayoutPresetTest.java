package com.barnwatch9000.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridLayoutPresetTest
{
    @Test
    void everyPresetCoversItsGridWithoutOverlap()
    {
        for (GridLayoutPreset preset : GridLayoutPreset.values())
        {
            Set<String> occupiedCells = new HashSet<>();
            for (GridLayoutPreset.TilePlacement placement : preset.placements())
            {
                assertTrue(placement.column() >= 0);
                assertTrue(placement.row() >= 0);
                assertTrue(placement.columnSpan() > 0);
                assertTrue(placement.rowSpan() > 0);
                assertTrue(placement.column() + placement.columnSpan() <= preset.columns());
                assertTrue(placement.row() + placement.rowSpan() <= preset.rows());

                for (int row = placement.row(); row < placement.row() + placement.rowSpan(); row++)
                {
                    for (int column = placement.column(); column < placement.column() + placement.columnSpan(); column++)
                    {
                        assertTrue(occupiedCells.add(column + ":" + row), preset + " contains overlapping tiles");
                    }
                }
            }

            assertEquals(preset.columns() * preset.rows(), occupiedCells.size(), preset + " leaves unused grid cells");
            assertEquals(preset.placements().size(), preset.capacity());
        }
    }

    @Test
    void storedValuesAcceptLabelsAndEnumNames()
    {
        assertEquals(GridLayoutPreset.BIG_SEVEN, GridLayoutPreset.fromStoredValue("Big 7").orElseThrow());
        assertEquals(GridLayoutPreset.BIG_SEVEN, GridLayoutPreset.fromStoredValue("BIG_SEVEN").orElseThrow());
        assertTrue(GridLayoutPreset.fromStoredValue("unknown").isEmpty());
    }
}
