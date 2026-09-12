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

package com.faendir.zachtronics.bot.sc.repository;

import com.faendir.zachtronics.bot.git.GitRepository;
import com.faendir.zachtronics.bot.model.DisplayContext;
import com.faendir.zachtronics.bot.model.StringFormat;
//import com.faendir.zachtronics.bot.reddit.RedditService;
//import com.faendir.zachtronics.bot.reddit.Subreddit;
import com.faendir.zachtronics.bot.repository.AbstractSolutionRepository;
import com.faendir.zachtronics.bot.repository.SubmitResult;
import com.faendir.zachtronics.bot.sc.model.*;
import com.faendir.zachtronics.bot.utils.Markdown;
import com.faendir.zachtronics.bot.validation.ValidationResult;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import static com.faendir.zachtronics.bot.sc.model.ScCategory.*;
import static com.faendir.zachtronics.bot.sc.model.ScMetric.*;

@Component
@RequiredArgsConstructor
@Getter(AccessLevel.PROTECTED)
public class ScSolutionRepository extends AbstractSolutionRepository<ScCategory, ScPuzzle, ScScore, ScSubmission, ScRecord, ScSolution> {
    private final ScCategory[][] wikiCategories = {{ C,  CNB,  CNP,  CNBP}, { S,  SNB,  SNP,  SNBP},
                                                   {RC, RCNB, RCNP, RCNBP}, {RS, RSNB, RSNP, RSNBP}};
    //private final RedditService redditService;
    //private final Subreddit subreddit = Subreddit.SPACECHEM;

    @Qualifier("scArchiveRepository")
    private final GitRepository gitRepo;
    private final Class<ScCategory> categoryClass = ScCategory.class;
    private final Function<String[], ScSolution> solUnmarshaller = ScSolution::unmarshal;
    private final List<Comparator<ScScore>> frontierComparators = List.of(CYCLES, REACTORS, SYMBOLS, NO_BUGS, NO_PRECOG);
    private final List<ScPuzzle> trackedPuzzles = Arrays.stream(ScPuzzle.values()).filter(p -> p.getType() != ScType.BOSS_RANDOM).toList();

    @Override
    public SubmitResult<ScRecord, ScCategory> submit(ScSubmission submission) {
        try (GitRepository.ReadWriteAccess access = gitRepo.acquireWriteAccess()) {
            BiConsumer<ScSubmission, Collection<ScCategory>> successCallback = (sub, wonCategories) -> {
                access.push();
                if (!wonCategories.isEmpty()) {
                }
            };
            return submitOne(access, submission, successCallback);
        }
    }

    @Override
    protected String wikiPageName(ScPuzzle puzzle) {
        return puzzle.getGroup().getWikiPage();
    }

    @Override
    public List<SubmitResult<ScRecord, ScCategory>> submitAll(
            Collection<? extends ValidationResult<ScSubmission>> validationResults) {
        try (GitRepository.ReadWriteAccess access = gitRepo.acquireWriteAccess()) {
            List<SubmitResult<ScRecord, ScCategory>> submitResults = new ArrayList<>();
            BiConsumer<ScSubmission, Collection<ScCategory>> successCallback = (sub, wonCategories) -> {
            };

            for (ValidationResult<ScSubmission> validationResult : validationResults) {
                if (validationResult instanceof ValidationResult.Valid<ScSubmission>) {
                    ScSubmission submission = validationResult.getSubmission();
                    submitResults.add(submitOne(access, submission, successCallback));
                }
                else {
                    submitResults.add(new SubmitResult.Failure<>(validationResult.getMessage()));
                }
            }

            access.push();
            return submitResults;
        }
    }

    private static String makeLeaderboardCell(@Nullable ScRecord[] blockRecords, int i, int minReactors,
                                              DisplayContext<ScCategory> displayContext) {
        ScRecord record = blockRecords[i];
        assert record != null;
        for (int prev = 0; prev < i; prev++) {
            if (record == blockRecords[prev]) {
                return "←".repeat(i - prev);
            }
        }

        String reactorPrefix = (record.getScore().getReactors() > minReactors) ? "† " : "";
        return record.toDisplayString(displayContext, reactorPrefix);
    }


    @Override
    protected ScSolution makeCandidateSolution(ScSubmission submission) {
        return new ScSolution(submission.getScore(), submission.getAuthor(), submission.getDisplayLink(), false);
    }

    @Override
    protected void removeOrReplaceFromIndex(ScSolution candidate, ScSolution solution,
                                            ListIterator<ScSolution> it) {
        if (candidate.getDisplayLink() == null && solution.getDisplayLink() != null) {
            // we beat the solution, but we can't replace the video, we keep the solution entry as a video-only
            it.set(solution.withVideoOnly(true)); // empty categories
        }
        else {
            it.remove();
        }
    }

    @Override
    protected Path relativePuzzlePath(ScPuzzle puzzle) {
        return Path.of(puzzle.getGroup().name(), puzzle.name());
    }

    static String makeScoreFilename(ScScore score) {
        return score.toDisplayString(DisplayContext.fileName()) + ".txt";
    }

    @Override
    protected String makeArchiveLink(ScPuzzle puzzle, ScScore score) {
        return makeArchiveLink(puzzle, makeScoreFilename(score));
    }

    @Override
    protected Path makeArchivePath(Path puzzlePath, ScScore score) {
        return puzzlePath.resolve(makeScoreFilename(score));
    }
}
