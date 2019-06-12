/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016, 2017  Waltz open source project
 * See README.md for more information
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.khartec.waltz.data.change_unit;

import com.khartec.waltz.data.IdSelectorFactory;
import com.khartec.waltz.model.EntityKind;
import com.khartec.waltz.model.EntityReference;
import com.khartec.waltz.model.IdSelectionOptions;
import org.jooq.Condition;
import org.jooq.Record1;
import org.jooq.Select;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import static com.khartec.waltz.common.Checks.checkNotNull;
import static com.khartec.waltz.data.SelectorUtilities.ensureScopeIsExact;
import static com.khartec.waltz.schema.tables.ChangeUnit.CHANGE_UNIT;
import static com.khartec.waltz.schema.tables.FlowDiagramEntity.FLOW_DIAGRAM_ENTITY;
import static com.khartec.waltz.schema.tables.LogicalFlow.LOGICAL_FLOW;
import static com.khartec.waltz.schema.tables.PhysicalFlow.PHYSICAL_FLOW;

@Service
public class ChangeUnitIdSelectorFactory implements IdSelectorFactory {


    @Override
    public Select<Record1<Long>> apply(IdSelectionOptions options) {
        checkNotNull(options, "options cannot be null");
        switch(options.entityReference().kind()) {
            case APPLICATION:
                // all physical flows where the app is a source or target
                return mkForFlowEndpoint(options);
            case FLOW_DIAGRAM:
                // given a flow diagram, find all change units associated to the entities in the diagram
                return mkForDiagram(options);
            case PHYSICAL_FLOW:
                return mkForDirectEntity(options);
            default:
                throw new UnsupportedOperationException("Cannot create Change Unit selector from options: " + options);
        }
    }

    private Select<Record1<Long>> mkForDiagram(IdSelectionOptions options) {
        ensureScopeIsExact(options);
        EntityReference entityReference = options.entityReference();

        return DSL
                .select(CHANGE_UNIT.ID)
                .from(CHANGE_UNIT)
                .innerJoin(PHYSICAL_FLOW).on(PHYSICAL_FLOW.ID.eq(CHANGE_UNIT.SUBJECT_ENTITY_ID)
                        .and(CHANGE_UNIT.SUBJECT_ENTITY_KIND.eq(EntityKind.PHYSICAL_FLOW.name())))
                .innerJoin(LOGICAL_FLOW).on(LOGICAL_FLOW.ID.eq(PHYSICAL_FLOW.LOGICAL_FLOW_ID))
                .innerJoin(FLOW_DIAGRAM_ENTITY)
                    .on(LOGICAL_FLOW.SOURCE_ENTITY_KIND.eq(FLOW_DIAGRAM_ENTITY.ENTITY_KIND)
                        .and(LOGICAL_FLOW.SOURCE_ENTITY_ID.eq(FLOW_DIAGRAM_ENTITY.ENTITY_ID)))
                    .or(LOGICAL_FLOW.TARGET_ENTITY_KIND.eq(FLOW_DIAGRAM_ENTITY.ENTITY_KIND)
                        .and(LOGICAL_FLOW.TARGET_ENTITY_ID.eq(FLOW_DIAGRAM_ENTITY.ENTITY_ID)))
                .where(FLOW_DIAGRAM_ENTITY.DIAGRAM_ID.eq(entityReference.id()));
    }


    private Select<Record1<Long>> mkForDirectEntity(IdSelectionOptions options) {
        ensureScopeIsExact(options);
        EntityReference ref = options.entityReference();
        return DSL
                .select(CHANGE_UNIT.ID)
                .from(CHANGE_UNIT)
                .where(CHANGE_UNIT.SUBJECT_ENTITY_ID.eq(ref.id()))
                .and(CHANGE_UNIT.SUBJECT_ENTITY_KIND.eq(ref.kind().name()));
    }


    private Select<Record1<Long>> mkForFlowEndpoint(IdSelectionOptions options) {
        ensureScopeIsExact(options);
        EntityReference ref = options.entityReference();

        Condition sourceRef = LOGICAL_FLOW.SOURCE_ENTITY_KIND.eq(ref.kind().name())
                .and(LOGICAL_FLOW.SOURCE_ENTITY_ID.eq(ref.id()));

        Condition targetRef = LOGICAL_FLOW.TARGET_ENTITY_KIND.eq(ref.kind().name())
                .and(LOGICAL_FLOW.TARGET_ENTITY_ID.eq(ref.id()));

        return DSL
                .select(CHANGE_UNIT.ID)
                .from(CHANGE_UNIT)
                .innerJoin(PHYSICAL_FLOW).on(PHYSICAL_FLOW.ID.eq(CHANGE_UNIT.SUBJECT_ENTITY_ID)
                        .and(CHANGE_UNIT.SUBJECT_ENTITY_KIND.eq(EntityKind.PHYSICAL_FLOW.name())))
                .innerJoin(LOGICAL_FLOW).on(LOGICAL_FLOW.ID.eq(PHYSICAL_FLOW.LOGICAL_FLOW_ID))
                .where(sourceRef.or(targetRef));
    }


    private Select<Record1<Long>> mkForLogicalFlow(IdSelectionOptions options) {
        ensureScopeIsExact(options);
        EntityReference ref = options.entityReference();

        return DSL
                .select(CHANGE_UNIT.ID)
                .from(CHANGE_UNIT)
                .innerJoin(PHYSICAL_FLOW).on(PHYSICAL_FLOW.ID.eq(CHANGE_UNIT.SUBJECT_ENTITY_ID)
                        .and(CHANGE_UNIT.SUBJECT_ENTITY_KIND.eq(EntityKind.PHYSICAL_FLOW.name())))
                .innerJoin(LOGICAL_FLOW).on(LOGICAL_FLOW.ID.eq(PHYSICAL_FLOW.LOGICAL_FLOW_ID))
                .where(LOGICAL_FLOW.ID.eq(ref.id()));
    }

}