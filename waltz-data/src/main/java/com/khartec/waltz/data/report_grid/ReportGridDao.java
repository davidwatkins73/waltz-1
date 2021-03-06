/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016, 2017, 2018, 2019 Waltz open source project
 * See README.md for more information
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific
 *
 */

package com.khartec.waltz.data.report_grid;


import com.khartec.waltz.common.SetUtilities;
import com.khartec.waltz.model.EntityKind;
import com.khartec.waltz.model.EntityReference;
import com.khartec.waltz.model.report_grid.*;
import com.khartec.waltz.schema.Tables;
import com.khartec.waltz.schema.tables.records.ReportGridRecord;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.lambda.tuple.Tuple2;
import org.jooq.lambda.tuple.Tuple3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.*;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import static com.khartec.waltz.common.DateTimeUtilities.toLocalDateTime;
import static com.khartec.waltz.common.MapUtilities.groupBy;
import static com.khartec.waltz.common.SetUtilities.map;
import static com.khartec.waltz.common.SetUtilities.union;
import static com.khartec.waltz.model.EntityReference.mkRef;
import static com.khartec.waltz.schema.Tables.*;
import static com.khartec.waltz.schema.tables.InvolvementKind.INVOLVEMENT_KIND;
import static java.util.Collections.emptySet;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.jooq.lambda.tuple.Tuple.tuple;

@Repository
public class ReportGridDao {

    private final DSLContext dsl;

    private final com.khartec.waltz.schema.tables.Measurable m = MEASURABLE.as("m");
    private final com.khartec.waltz.schema.tables.MeasurableRating mr = MEASURABLE_RATING.as("mr");
    private final com.khartec.waltz.schema.tables.MeasurableCategory mc = MEASURABLE_CATEGORY.as("mc");
    private final com.khartec.waltz.schema.tables.ReportGridColumnDefinition rgcd = REPORT_GRID_COLUMN_DEFINITION.as("rgcd");
    private final com.khartec.waltz.schema.tables.ReportGrid rg = Tables.REPORT_GRID.as("rg");
    private final com.khartec.waltz.schema.tables.RatingSchemeItem rsi = RATING_SCHEME_ITEM.as("rsi");
    private final com.khartec.waltz.schema.tables.EntityHierarchy eh = ENTITY_HIERARCHY.as("eh");
    private final com.khartec.waltz.schema.tables.AssessmentDefinition ad = ASSESSMENT_DEFINITION.as("ad");
    private final com.khartec.waltz.schema.tables.AssessmentRating ar = ASSESSMENT_RATING.as("ar");
    private final com.khartec.waltz.schema.tables.CostKind ck = COST_KIND.as("ck");
    private final com.khartec.waltz.schema.tables.Cost c = COST.as("c");
    private final com.khartec.waltz.schema.tables.Involvement inv = INVOLVEMENT.as("inv");
    private final com.khartec.waltz.schema.tables.InvolvementKind ik  = INVOLVEMENT_KIND.as("ik");
    private final com.khartec.waltz.schema.tables.Person p = PERSON.as("p");


    @Autowired
    public ReportGridDao(DSLContext dsl) {
        this.dsl = dsl;
    }


    public Set<ReportGridDefinition> findAll(){
        return dsl
                .selectFrom(rg)
                .fetchSet(r -> mkReportGridDefinition(rgcd.REPORT_GRID_ID.eq(r.get(rg.ID)), r));
    }


    public Set<ReportGridCell> findCellDataByGridId(long id,
                                                    Select<Record1<Long>> appSelector) {
        return findCellDataByGridCondition(rg.ID.eq(id), appSelector);
    }


    public Set<ReportGridCell> findCellDataByGridExternalId(String  externalId,
                                                            Select<Record1<Long>> appSelector) {
        return findCellDataByGridCondition(rg.EXTERNAL_ID.eq(externalId), appSelector);
    }


    public ReportGridDefinition getGridDefinitionById(long id) {
        return getGridDefinitionByCondition(rg.ID.eq(id));
    }


    public ReportGridDefinition getGridDefinitionByExternalId(String externalId) {
        return getGridDefinitionByCondition(rg.EXTERNAL_ID.eq(externalId));
    }


    // --- Helpers ---

    private ReportGridDefinition getGridDefinitionByCondition(Condition condition) {
        return dsl
                .selectFrom(rg)
                .where(condition)
                .fetchOne(r -> mkReportGridDefinition(condition, r));
    }


    private List<ReportGridColumnDefinition> getColumnDefinitions(Condition condition) {

        SelectConditionStep<Record7<String, Long, String, String, Integer, String, String>> measurableColumns = mkColumnDefinitionQuery(
                EntityKind.MEASURABLE,
                m,
                m.ID,
                m.NAME,
                m.DESCRIPTION,
                condition);

        SelectConditionStep<Record7<String, Long, String, String, Integer, String, String>> assessmentDefinitionColumns = mkColumnDefinitionQuery(
                EntityKind.ASSESSMENT_DEFINITION,
                ad,
                ad.ID,
                ad.NAME,
                ad.DESCRIPTION,
                condition);

        SelectConditionStep<Record7<String, Long, String, String, Integer, String, String>> costKindColumns = mkColumnDefinitionQuery(
                EntityKind.COST_KIND,
                ck,
                ck.ID,
                ck.NAME,
                ck.DESCRIPTION,
                condition);

        SelectConditionStep<Record7<String, Long, String, String, Integer, String, String>> involvementKindColumns = mkColumnDefinitionQuery(
                EntityKind.INVOLVEMENT_KIND,
                ik,
                ik.ID,
                ik.NAME,
                ik.DESCRIPTION,
                condition);

        return assessmentDefinitionColumns
                .unionAll(measurableColumns)
                .unionAll(costKindColumns)
                .unionAll(involvementKindColumns)
                .orderBy(rgcd.POSITION, DSL.field("name"))
                .fetch(r -> ImmutableReportGridColumnDefinition.builder()
                        .columnEntityReference(mkRef(
                                EntityKind.valueOf(r.get(rgcd.COLUMN_ENTITY_KIND)),
                                r.get(rgcd.COLUMN_ENTITY_ID),
                                r.get("name", String.class),
                                r.get("desc", String.class)))
                        .position(r.get(rgcd.POSITION))
                        .usageKind(ColumnUsageKind.valueOf(r.get(rgcd.COLUMN_USAGE_KIND)))
                        .ratingRollupRule(RatingRollupRule.valueOf(r.get(rgcd.RATING_ROLLUP_RULE)))
                        .build());
    }

    private SelectConditionStep<Record7<String, Long, String, String, Integer, String, String>> mkColumnDefinitionQuery(EntityKind entityKind,
                                                                                                                        Table<?> t,
                                                                                                                        TableField<? extends Record, Long> ID,
                                                                                                                        TableField<? extends Record, String> NAME,
                                                                                                                        TableField<? extends Record, String> DESCRIPTION,
                                                                                                                        Condition reportCondition) {
        return dsl
                    .select(DSL.coalesce(rgcd.DISPLAY_NAME, NAME).as("name"),
                            rgcd.COLUMN_ENTITY_ID,
                            rgcd.COLUMN_ENTITY_KIND,
                            DESCRIPTION.as("desc"),
                            rgcd.POSITION,
                            rgcd.COLUMN_USAGE_KIND,
                            rgcd.RATING_ROLLUP_RULE)
                    .from(rgcd)
                    .innerJoin(t).on(ID.eq(rgcd.COLUMN_ENTITY_ID).and(rgcd.COLUMN_ENTITY_KIND.eq(entityKind.name())))
                    .innerJoin(rg).on(rg.ID.eq(rgcd.REPORT_GRID_ID))
                    .where(reportCondition);
    }



    private Set<ReportGridCell> findCellDataByGridCondition(Condition gridCondition,
                                                            Select<Record1<Long>> appSelector) {

        ReportGridDefinition gridDefn = getGridDefinitionByCondition(gridCondition);

        Map<EntityKind, Collection<ReportGridColumnDefinition>> colsByKind = groupBy(
                gridDefn.columnDefinitions(),
                cd -> cd.columnEntityReference().kind());

        Set<Long> requiredAssessmentDefinitions = map(
                colsByKind.getOrDefault(EntityKind.ASSESSMENT_DEFINITION, emptySet()),
                cd -> cd.columnEntityReference().id());

        Map<RatingRollupRule, Collection<ReportGridColumnDefinition>> measurableColumnsByRollupKind = groupBy(
                colsByKind.getOrDefault(EntityKind.MEASURABLE, emptySet()),
                ReportGridColumnDefinition::ratingRollupRule);

        Set<Long> exactMeasurableIds = map(
                measurableColumnsByRollupKind.get(RatingRollupRule.NONE),
                cd -> cd.columnEntityReference().id());

        Set<Long> summaryMeasurableIdsUsingHighest = map(
                measurableColumnsByRollupKind.get(RatingRollupRule.PICK_HIGHEST),
                cd -> cd.columnEntityReference().id());

        Set<Long> summaryMeasurableIdsUsingLowest = map(
                measurableColumnsByRollupKind.get(RatingRollupRule.PICK_LOWEST),
                cd -> cd.columnEntityReference().id());

        Set<Long> requiredCostKinds = map(
                colsByKind.getOrDefault(EntityKind.COST_KIND, emptySet()),
                cd -> cd.columnEntityReference().id());

        Set<Long> requiredInvolvementKinds = map(
                colsByKind.getOrDefault(EntityKind.INVOLVEMENT_KIND, emptySet()),
                cd -> cd.columnEntityReference().id());


        return union(
                fetchSummaryMeasurableData(appSelector, summaryMeasurableIdsUsingHighest, summaryMeasurableIdsUsingLowest),
                fetchAssessmentData(appSelector, requiredAssessmentDefinitions),
                fetchExactMeasurableData(appSelector, exactMeasurableIds),
                fetchCostData(appSelector, requiredCostKinds),
                fetchInvolvementData(appSelector, requiredInvolvementKinds));
    }


    private Set<ReportGridCell> fetchInvolvementData(Select<Record1<Long>> appSelector,
                                                     Set<Long> requiredInvolvementKinds) {
        if (requiredInvolvementKinds.size() == 0) {
            return emptySet();
        } else {
            return SetUtilities.fromCollection(dsl
                    .select(
                            inv.ENTITY_ID,
                            inv.KIND_ID,
                            p.EMAIL)
                    .from(inv)
                    .innerJoin(p).on(p.EMPLOYEE_ID.eq(inv.EMPLOYEE_ID))
                    .where(inv.ENTITY_KIND.eq(EntityKind.APPLICATION.name()))
                    .and(inv.ENTITY_ID.in(appSelector))
                    .and(inv.KIND_ID.in(requiredInvolvementKinds))
                    .and(p.IS_REMOVED.isFalse())
                    .fetchSet(r -> ImmutableReportGridCell
                            .builder()
                            .applicationId(r.get(inv.ENTITY_ID))
                            .columnEntityId(r.get(inv.KIND_ID))
                            .columnEntityKind(EntityKind.INVOLVEMENT_KIND)
                            .text(r.get(p.EMAIL))
                            .build())
                    // we now convert to a map so we can merge text values of cells with the same coordinates (appId, entId)
                    .stream()
                    .collect(toMap(
                            c -> tuple(c.applicationId(), c.columnEntityId()),
                            identity(),
                            (a, b) -> ImmutableReportGridCell
                                    .copyOf(a)
                                    .withText(a.text() + "; " + b.text())))
                    // and then we simply return the values of that temporary map.
                    .values());
        }
    }


    private Set<ReportGridCell> fetchCostData(Select<Record1<Long>> appSelector,
                                              Set<Long> requiredCostKinds) {

        if (requiredCostKinds.size() == 0) {
            return emptySet();
        } else {

            SelectHavingStep<Record2<Long, Integer>> costKindLastestYear = dsl
                    .select(COST.COST_KIND_ID, DSL.max(COST.YEAR).as("latest_year"))
                    .from(COST)
                    .where(dsl.renderInlined(COST.ENTITY_ID.in(appSelector)
                            .and(COST.ENTITY_KIND.eq(EntityKind.APPLICATION.name()))))
                    .groupBy(COST.COST_KIND_ID);

            Condition latestYearForKind = c.COST_KIND_ID.eq(costKindLastestYear.field(COST.COST_KIND_ID))
                    .and(c.YEAR.eq(costKindLastestYear.field("latest_year", Integer.class)));

            return dsl
                    .select(c.ENTITY_ID,
                            c.COST_KIND_ID,
                            c.AMOUNT)
                    .from(c)
                    .innerJoin(costKindLastestYear).on(latestYearForKind)
                    .where(dsl.renderInlined(c.COST_KIND_ID.in(requiredCostKinds)
                            .and(c.ENTITY_KIND.eq(EntityKind.APPLICATION.name()))
                            .and(c.ENTITY_ID.in(appSelector))))
                    .fetchSet(r -> ImmutableReportGridCell.builder()
                            .applicationId(r.get(c.ENTITY_ID))
                            .columnEntityId(r.get(c.COST_KIND_ID))
                            .columnEntityKind(EntityKind.COST_KIND)
                            .value(r.get(c.AMOUNT))
                            .build());
        }
    }


    private Set<ReportGridCell> fetchSummaryMeasurableData(Select<Record1<Long>> appSelector,
                                                           Set<Long> measurableIdsUsingHighest,
                                                           Set<Long> measurableIdsUsingLowest) {

        if (measurableIdsUsingHighest.size() == 0 && measurableIdsUsingLowest.size() == 0){
            return emptySet();
        }

        Table<Record5<Long, String, Long, Integer, String>> ratingSchemeItems = DSL
                .select(mc.ID.as("mcId"),
                        rsi.CODE.as("rsiCode"),
                        rsi.ID.as("rsiId"),
                        rsi.POSITION.as("rsiPos"),
                        rsi.NAME.as("rsiName"))
                .from(mc)
                .innerJoin(rsi)
                .on(rsi.SCHEME_ID.eq(mc.RATING_SCHEME_ID))
                .asTable("ratingSchemeItems");

        SelectConditionStep<Record5<Long, Long, Long, Integer, String>> ratings = DSL
                .select(
                        m.ID,
                        mr.ENTITY_ID,
                        ratingSchemeItems.field("rsiId", Long.class),
                        ratingSchemeItems.field("rsiPos", Integer.class),
                        ratingSchemeItems.field("rsiName", String.class))
                .from(m)
                .innerJoin(eh)
                    .on(eh.ANCESTOR_ID.eq(m.ID))
                    .and(eh.KIND.eq(EntityKind.MEASURABLE.name()))
                .innerJoin(mr)
                    .on(mr.MEASURABLE_ID.eq(eh.ID))
                .innerJoin(ratingSchemeItems)
                    .on(m.MEASURABLE_CATEGORY_ID.eq(ratingSchemeItems.field("mcId", Long.class)))
                    .and(mr.RATING.eq(ratingSchemeItems.field("rsiCode", String.class)))
                .where(mr.ENTITY_KIND.eq(EntityKind.APPLICATION.name()))
                .and(mr.ENTITY_ID.in(appSelector))
                .and(m.ID.in(union(measurableIdsUsingHighest, measurableIdsUsingLowest)));

        return dsl
                .resultQuery(dsl.renderInlined(ratings))
                .fetchGroups(
                        r -> tuple(
                                mkRef(EntityKind.APPLICATION, r.get(mr.ENTITY_ID)),
                                r.get(m.ID)),
                        r -> tuple(
                                r.get("rsiId", Long.class),
                                r.get("rsiPos", Integer.class),
                                r.get("rsiName", String.class)))
                .entrySet()
                .stream()
                .map(e -> {
                    Tuple2<EntityReference, Long> entityAndMeasurable = e.getKey();
                    Long measurableId = entityAndMeasurable.v2();
                    long applicationId = entityAndMeasurable.v1().id();
                    List<Tuple3<Long, Integer, String>> ratingsForEntityAndMeasurable = e.getValue();

                    ToIntFunction<Tuple3<Long, Integer, String>> compareByPositionAsc = t -> t.v2;
                    ToIntFunction<Tuple3<Long, Integer, String>> compareByPositionDesc = t -> t.v2 * -1;
                    Function<? super Tuple3<Long, Integer, String>, String> compareByName = t -> t.v3;

                    Comparator<Tuple3<Long, Integer, String>> cmp = Comparator
                            .comparingInt(measurableIdsUsingHighest.contains(measurableId)
                                    ? compareByPositionAsc
                                    : compareByPositionDesc)
                            .thenComparing(compareByName);

                    return ratingsForEntityAndMeasurable
                            .stream()
                            .min(cmp)
                            .map(t -> ImmutableReportGridCell
                                    .builder()
                                    .applicationId(applicationId)
                                    .columnEntityId(measurableId)
                                    .columnEntityKind(EntityKind.MEASURABLE)
                                    .ratingId(t.v1)
                                    .build())
                            .orElse(null);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }


    private Set<ReportGridCell> fetchExactMeasurableData(Select<Record1<Long>> appSelector,
                                                         Set<Long> exactMeasurableIds) {

        if (exactMeasurableIds.size() == 0) {
            return emptySet();
        }

        SelectConditionStep<Record4<Long, Long, Long, String>> qry = dsl
                .select(mr.ENTITY_ID,
                        mr.MEASURABLE_ID,
                        rsi.ID,
                        mr.DESCRIPTION)
                .from(mr)
                .innerJoin(m).on(m.ID.eq(mr.MEASURABLE_ID))
                .innerJoin(mc).on(mc.ID.eq(m.MEASURABLE_CATEGORY_ID))
                .innerJoin(rsi).on(rsi.CODE.eq(mr.RATING)).and(rsi.SCHEME_ID.eq(mc.RATING_SCHEME_ID))
                .where(mr.MEASURABLE_ID.in(exactMeasurableIds))
                .and(mr.ENTITY_ID.in(appSelector))
                .and(mr.ENTITY_KIND.eq(EntityKind.APPLICATION.name()));

        return  dsl
                .resultQuery(dsl.renderInlined(qry))
                .fetchSet(r -> ImmutableReportGridCell.builder()
                        .applicationId(r.get(mr.ENTITY_ID))
                        .columnEntityId(r.get(mr.MEASURABLE_ID))
                        .columnEntityKind(EntityKind.MEASURABLE)
                        .ratingId(r.get(rsi.ID))
                        .comment(r.get(mr.DESCRIPTION))
                        .build());
    }


    private Set<ReportGridCell> fetchAssessmentData(Select<Record1<Long>> appSelector,
                                                    Set<Long> requiredAssessmentDefinitionIds) {
        if (requiredAssessmentDefinitionIds.size() == 0) {
            return emptySet();
        } else {
            return dsl
                    .select(ar.ENTITY_ID,
                            ar.ASSESSMENT_DEFINITION_ID,
                            ar.RATING_ID,
                            ar.DESCRIPTION)
                    .from(ar)
                    .where(ar.ASSESSMENT_DEFINITION_ID.in(requiredAssessmentDefinitionIds)
                            .and(ar.ENTITY_KIND.eq(EntityKind.APPLICATION.name()))
                            .and(ar.ENTITY_ID.in(appSelector)))
                    .fetchSet(r -> ImmutableReportGridCell.builder()
                            .applicationId(r.get(ar.ENTITY_ID))
                            .columnEntityId(r.get(ar.ASSESSMENT_DEFINITION_ID))
                            .columnEntityKind(EntityKind.ASSESSMENT_DEFINITION)
                            .ratingId(r.get(ar.RATING_ID))
                            .comment(r.get(ar.DESCRIPTION))
                            .build());
        }
    }


    private ImmutableReportGridDefinition mkReportGridDefinition(Condition condition,
                                                                 ReportGridRecord r) {
        return ImmutableReportGridDefinition
                .builder()
                .id(r.get(rg.ID))
                .name(r.get(rg.NAME))
                .description(r.get(rg.DESCRIPTION))
                .externalId(Optional.ofNullable(r.get(rg.EXTERNAL_ID)))
                .provenance(r.get(rg.PROVENANCE))
                .lastUpdatedAt(toLocalDateTime(r.get(rg.LAST_UPDATED_AT)))
                .lastUpdatedBy(r.get(rg.LAST_UPDATED_BY))
                .columnDefinitions(getColumnDefinitions(condition))
                .build();
    }
}

