/*
 *  This file is part of Waltz.
 *
 *     Waltz is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Waltz is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Waltz.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.khartec.waltz.data.server_information;

import com.khartec.waltz.model.server_information.ImmutableServerInformation;
import com.khartec.waltz.model.server_information.ImmutableServerSummaryStatistics;
import com.khartec.waltz.model.server_information.ServerInformation;
import com.khartec.waltz.model.server_information.ServerSummaryStatistics;
import com.khartec.waltz.model.tally.Tally;
import com.khartec.waltz.schema.tables.records.ServerInformationRecord;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.lambda.Unchecked;
import org.jooq.lambda.tuple.Tuple;
import org.jooq.lambda.tuple.Tuple2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static com.khartec.waltz.common.Checks.checkNotNull;
import static com.khartec.waltz.common.DateTimeUtilities.toSqlDate;
import static com.khartec.waltz.data.JooqUtilities.DB_EXECUTOR_POOL;
import static com.khartec.waltz.data.JooqUtilities.calculateStringTallies;
import static com.khartec.waltz.schema.tables.Application.APPLICATION;
import static com.khartec.waltz.schema.tables.ServerInformation.SERVER_INFORMATION;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.select;


@Repository
public class ServerInformationDao {


    private final DSLContext dsl;


    private final RecordMapper<Record, ServerInformation> recordMapper = r -> {

        ServerInformationRecord row = r.into(ServerInformationRecord.class);

        boolean isVirtual = row.getIsVirtual() == null
                ? false
                : row.getIsVirtual();

        return ImmutableServerInformation.builder()
                .id(row.getId())
                .assetCode(row.getAssetCode())
                .hostname(row.getHostname())
                .virtual(isVirtual)
                .operatingSystem(row.getOperatingSystem())
                .operatingSystemVersion(row.getOperatingSystemVersion())
                .environment(row.getEnvironment())
                .location(row.getLocation())
                .country(row.getCountry())
                .provenance(row.getProvenance())
                .hardwareEndOfLifeDate(row.getHwEndOfLifeDate())
                .operatingSystemEndOfLifeDate(row.getOsEndOfLifeDate())
                .build();
    };


    @Autowired
    public ServerInformationDao(DSLContext dsl) {
        checkNotNull(dsl, "dsl must not be null");
        this.dsl = dsl;
    }


    public List<ServerInformation> findByAssetCode(String assetCode) {
        return dsl.select()
                .from(SERVER_INFORMATION)
                .where(SERVER_INFORMATION.ASSET_CODE.eq(assetCode))
                .fetch(recordMapper);
    }


    public List<ServerInformation> findByAppId(long appId) {
        return dsl.select(SERVER_INFORMATION.fields())
                .from(SERVER_INFORMATION)
                .innerJoin(APPLICATION)
                .on(SERVER_INFORMATION.ASSET_CODE.in(APPLICATION.ASSET_CODE, APPLICATION.ASSET_CODE))
                .where(APPLICATION.ID.eq(appId))
                .fetch(recordMapper);
    }


    public int[] bulkSave(List<ServerInformation> servers) {
        return dsl
                .batch(servers.stream()
                    .map(s -> dsl
                            .insertInto(
                                    SERVER_INFORMATION,
                                    SERVER_INFORMATION.HOSTNAME,
                                    SERVER_INFORMATION.OPERATING_SYSTEM,
                                    SERVER_INFORMATION.OPERATING_SYSTEM_VERSION,
                                    SERVER_INFORMATION.COUNTRY,
                                    SERVER_INFORMATION.IS_VIRTUAL,
                                    SERVER_INFORMATION.ENVIRONMENT,
                                    SERVER_INFORMATION.LOCATION,
                                    SERVER_INFORMATION.ASSET_CODE,
                                    SERVER_INFORMATION.HW_END_OF_LIFE_DATE,
                                    SERVER_INFORMATION.OS_END_OF_LIFE_DATE,
                                    SERVER_INFORMATION.PROVENANCE)
                            .values(
                                    s.hostname(),
                                    s.operatingSystem(),
                                    s.operatingSystemVersion(),
                                    s.country(),
                                    s.virtual(),
                                    s.environment(),
                                    s.location(),
                                    s.assetCode(),
                                    toSqlDate(s.hardwareEndOfLifeDate()),
                                    toSqlDate(s.operatingSystemEndOfLifeDate()),
                                    s.provenance()))
                    .collect(Collectors.toList()))
                .execute();
    }


    public ServerSummaryStatistics findStatsForAppSelector(Select<Record1<Long>> appIdSelector) {

        Field<String> environment = DSL.field("environment", String.class);
        Field<String> operatingSystem = DSL.field("operating_system", String.class);
        Field<String> location = DSL.field("location", String.class);

        Condition condition = SERVER_INFORMATION.ASSET_CODE
                .in(dsl.select(APPLICATION.ASSET_CODE)
                    .from(APPLICATION)
                    .where(APPLICATION.ID.in(appIdSelector)));

        // de-duplicate host names, as one server can host multiple apps
        Table serverInfoSubSelect = DSL.select(
                    max(SERVER_INFORMATION.ENVIRONMENT).as(environment),
                    max(SERVER_INFORMATION.OPERATING_SYSTEM).as(operatingSystem),
                    max(SERVER_INFORMATION.LOCATION).as(location))
                .from(SERVER_INFORMATION)
                .where(condition)
                .groupBy(SERVER_INFORMATION.HOSTNAME)
                .asTable("server_info_sub_select");

        Future<Tuple2<Integer, Integer>> typePromise = DB_EXECUTOR_POOL.submit(() ->
                calculateVirtualAndPhysicalCounts(condition));

        Future<List<Tally<String>>> envPromise = DB_EXECUTOR_POOL.submit(() -> calculateStringTallies(
                dsl,
                serverInfoSubSelect,
                environment,
                DSL.trueCondition()));

        Future<List<Tally<String>>> osPromise = DB_EXECUTOR_POOL.submit(() -> calculateStringTallies(
                dsl,
                serverInfoSubSelect,
                operatingSystem,
                DSL.trueCondition()));

        Future<List<Tally<String>>> locationPromise = DB_EXECUTOR_POOL.submit(() -> calculateStringTallies(
                dsl,
                serverInfoSubSelect,
                location,
                DSL.trueCondition()));


        return Unchecked.supplier(() -> {
            Tuple2<Integer, Integer> virtualAndPhysicalCounts = typePromise.get();

            return ImmutableServerSummaryStatistics.builder()
                    .virtualCount(virtualAndPhysicalCounts.v1)
                    .physicalCount(virtualAndPhysicalCounts.v2)
                    .environmentCounts(envPromise.get())
                    .operatingSystemCounts(osPromise.get())
                    .locationCounts(locationPromise.get())
                    .build();
        }).get();


    }

    private Tuple2<Integer, Integer> calculateVirtualAndPhysicalCounts(Condition condition) {
        Field<Boolean> isVirtual = DSL.field("is_virtual", Boolean.class);

        Field<BigDecimal> virtualCount = DSL.coalesce(DSL.sum(
                DSL.choose(isVirtual)
                        .when(Boolean.TRUE, 1)
                        .otherwise(0)), BigDecimal.ZERO)
                .as("virtual_count");

        Field<BigDecimal> physicalCount = DSL.coalesce(DSL.sum(
                DSL.choose(isVirtual)
                        .when(Boolean.TRUE, 0)
                        .otherwise(1)), BigDecimal.ZERO)
                .as("physical_count");

        Select<Record2<BigDecimal, BigDecimal>> typeQuery = dsl.select(virtualCount, physicalCount)
                .from(select(max(SERVER_INFORMATION.IS_VIRTUAL).as(isVirtual))
                            .from(SERVER_INFORMATION)
                            .where(dsl.renderInlined(condition))
                            .groupBy(SERVER_INFORMATION.HOSTNAME));

        return typeQuery
                .fetchOne(r -> Tuple.tuple(
                        r.value1().intValue(),
                        r.value2().intValue()));
    }


}
