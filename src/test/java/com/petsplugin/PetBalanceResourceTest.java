package com.petsplugin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PetBalanceResourceTest {

    @Test
    void turtleWaterMovementProgressesWithoutReachingTheAttributeCapAtLevelTwo() throws IOException {
        String pets = Files.readString(Path.of("src/main/resources/pets.yml"));
        int turtleStart = pets.indexOf("  turtle:");
        int llamaStart = pets.indexOf("  llama:", turtleStart);
        String turtle = pets.substring(turtleStart, llamaStart);

        assertTrue(pets.contains("content_version: 2"), "pets.yml should migrate the old turtle value");
        assertTrue(turtle.contains("type: WATER_MOVEMENT_EFFICIENCY"));
        assertTrue(turtle.contains("value_per_level: 0.05"),
                "the turtle should reach 0.5 water efficiency at level 10, not the 1.0 cap at level 2");
    }
}
