/*
 * Copyright (c) 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.faendir.zachtronics.bot.tis.repository;

import com.faendir.zachtronics.bot.git.GitRepository;
import com.faendir.zachtronics.bot.model.DisplayContext;
//import com.faendir.zachtronics.bot.reddit.RedditService;
//import com.faendir.zachtronics.bot.reddit.Subreddit;
import com.faendir.zachtronics.bot.repository.AbstractSolutionRepository;
import com.faendir.zachtronics.bot.tis.model.*;
import com.faendir.zachtronics.bot.utils.Markdown;
import com.google.errorprone.annotations.CheckReturnValue;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.faendir.zachtronics.bot.tis.model.TISCategory.*;
import static com.faendir.zachtronics.bot.tis.model.TISMetric.*;
import static java.util.stream.Collectors.*;

@Component
@RequiredArgsConstructor
@Getter(AccessLevel.PROTECTED)
public class TISSolutionRepository extends AbstractSolutionRepository<TISCategory, TISPuzzle, TISScore, TISSubmission, TISRecord, TISSolution> {
    private final TISCategory[][] wikiCategories = {{CN, CI, CX}, {NC, NI, NX}, {IC, IN, IX}};
    //private final RedditService redditService;
    //private final Subreddit subreddit = Subreddit.TIS100;

    @Qualifier("tisRepository")
    private final GitRepository gitRepo;
    private final Class<TISCategory> categoryClass = TISCategory.class;
    private final Function<String[], TISSolution> solUnmarshaller = TISSolution::unmarshal;
    private final List<Comparator<TISScore>> frontierComparators = List.of(CYCLES, NODES, INSTRUCTIONS,
                                                                           ACHIEVEMENT.reversed(), CHEATING, HARDCODED);
    @Getter(AccessLevel.PUBLIC) @VisibleForTesting
    private final List<TISPuzzle> trackedPuzzles = Arrays.stream(TISPuzzle.values()).filter(p -> p.getType() != TISType.SANDBOX).toList();

    @Override
    protected String wikiPageName(@Nullable TISPuzzle puzzle) {
        return "index";
    }

    @Override
    protected TISSolution makeCandidateSolution(TISSubmission submission) {
        return new TISSolution(submission.getScore(), submission.getAuthor(), submission.getDisplayLink());
    }

    private static final DecimalFormat format = new DecimalFormat("0.##", new DecimalFormatSymbols(Locale.ENGLISH));
    @CheckReturnValue
    private static Stream<String> metaLeaderboardStream(List<TISSolution> allSolutions, Predicate<TISSolution> filterSol) {
        Map<String, Double> authorToAmount = new HashMap<>();
        for (TISSolution solution: allSolutions) {
            if (filterSol.test(solution)) {
                String[] authors;
                if (solution.getAuthor().contains("/")) {
                    authors = solution.getAuthor().split("/");
                }
                else {
                    authors = new String[]{solution.getAuthor()};
                }
                double part = 1.0 / authors.length;
                for (String author : authors)
                    authorToAmount.merge(author, part, Double::sum);
            }
        }
        return authorToAmount.entrySet()
                             .stream()
                             .collect(groupingBy(Map.Entry::getValue, mapping(Map.Entry::getKey, toList())))
                             .entrySet()
                             .stream()
                             .sorted(Collections.reverseOrder(Map.Entry.comparingByKey()))
                             .map(e -> "| " + format.format(e.getKey()) + " | " + e.getValue()
                                                                                   .stream()
                                                                                   .sorted(String.CASE_INSENSITIVE_ORDER)
                                                                                   .map(Markdown::escape)
                                                                                   .collect(joining(", ")));
    }

    @Override
    protected Path relativePuzzlePath(TISPuzzle puzzle) {
        return Path.of(puzzle.getGroup().name()).resolve(puzzle.getId());
    }

    static String makeFilename(String puzzleId, TISScore score) {
        return puzzleId + "." + score.toDisplayString(DisplayContext.fileName()) + ".txt";
    }

    @Override
    protected String makeArchiveLink(TISPuzzle puzzle, TISScore score) {
        return makeArchiveLink(puzzle, makeFilename(puzzle.getId(), score));
    }

    @Override
    protected Path makeArchivePath(Path puzzlePath, TISScore score) {
        return puzzlePath.resolve(makeFilename(puzzlePath.getFileName().toString(), score));
    }
}
