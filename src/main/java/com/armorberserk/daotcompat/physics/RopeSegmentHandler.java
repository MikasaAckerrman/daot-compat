/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 * 
 * Rope wrapping implementation adapted from yyon/grapplemod SegmentHandler.java
 * License: This is a structural adaptation for compatibility, not a direct copy.
 */
package com.armorberserk.daotcompat.physics;

import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.LinkedList;

/**
 * Rope segment handler for ODM rope wrapping around block edges.
 * 
 * When a rope intersects with a block, this handler:
 * 1. Detects the collision point
 * 2. Finds the corner edge
 * 3. Creates a "bend point" (segment) where the rope wraps around
 * 4. Recursively checks remaining rope parts
 * 
 * Adapted from: yyon/grapplemod SegmentHandler.java (371 lines)
 * - Replaced Vec with Vec3 (Minecraft native)
 * - Replaced GrapplemodUtils.rayTraceBlocks with level.clip()
 * - Removed networking (clientside-only)
 * - Simplified for single-rope use case
 */
@OnlyIn(Dist.CLIENT)
public class RopeSegmentHandler {
    
    // Segment points where rope wraps around block edges
    public LinkedList<Vec3> segments;
    
    // Direction of the bottom face of the block for each segment
    public LinkedList<Direction> segmentBottomSides;
    
    // Direction of the side face of the block for each segment
    public LinkedList<Direction> segmentTopSides;
    
    private Vec3 prevHookPos = null;
    private Vec3 prevPlayerPos = null;
    private double ropeLen = 0.0;
    
    // Offset values for bend point calculation
    private static final double BEND_OFFSET = 0.05;      // How far to offset from corner
    private static final double INTO_BLOCK = 0.05;       // How far into block to start search
    private static final int MAX_RECURSIONS = 10;        // Prevent infinite recursion
    
    /**
     * Initialize rope segment handler with hook and player positions.
     */
    public RopeSegmentHandler(Vec3 hookPos, Vec3 playerPos) {
        segments = new LinkedList<>();
        segments.add(hookPos);
        segments.add(playerPos);
        
        segmentBottomSides = new LinkedList<>();
        segmentBottomSides.add(null);
        segmentBottomSides.add(null);
        
        segmentTopSides = new LinkedList<>();
        segmentTopSides.add(null);
        segmentTopSides.add(null);
        
        this.prevHookPos = new Vec3(hookPos.x, hookPos.y, hookPos.z);
        this.prevPlayerPos = new Vec3(playerPos.x, playerPos.y, playerPos.z);
    }
    
    /**
     * Reset positions (e.g., when rope is released and re-grabbed).
     */
    public void forceSetPos(Vec3 hookPos, Vec3 playerPos) {
        this.prevHookPos = new Vec3(hookPos.x, hookPos.y, hookPos.z);
        this.prevPlayerPos = new Vec3(playerPos.x, playerPos.y, playerPos.z);
        segments.set(0, hookPos);
        segments.set(segments.size() - 1, playerPos);
    }
    
    /**
     * Update rope configuration based on current positions and rope length.
     * This is the main method called each tick.
     */
    public void update(Vec3 hookPos, Vec3 playerPos, double ropeLen, Level level) {
        if (prevHookPos == null) {
            prevHookPos = new Vec3(hookPos.x, hookPos.y, hookPos.z);
            prevPlayerPos = new Vec3(playerPos.x, playerPos.y, playerPos.z);
        }
        
        segments.set(0, hookPos);
        segments.set(segments.size() - 1, playerPos);
        this.ropeLen = ropeLen;
        
        // Check and remove invalid segments from player end
        while (segments.size() > 2) {
            int index = segments.size() - 2;
            Vec3 closest = segments.get(index);
            Direction bottomside = segmentBottomSides.get(index);
            Direction topside = segmentTopSides.get(index);
            
            Vec3 ropevec = playerPos.subtract(closest);
            Vec3 beforepoint = segments.get(index - 1);
            
            Vec3 edgevec = getNormal(bottomside).cross(getNormal(topside));
            Vec3 planenormal = beforepoint.subtract(closest).cross(edgevec);
            
            if (ropevec.dot(planenormal) > 0) {
                removeSegment(index);
            } else {
                break;
            }
        }
        
        // Check and remove invalid segments from hook end (if hook is moving)
        boolean movingHook = !hookPos.equals(prevHookPos);
        if (movingHook && segments.size() > 2) {
            int index = 1;
            Vec3 farthest = segments.get(index);
            Direction bottomside = segmentBottomSides.get(index);
            Direction topside = segmentTopSides.get(index);
            
            Vec3 ropevec = farthest.subtract(hookPos);
            Vec3 beforepoint = segments.get(index + 1);
            
            Vec3 edgevec = getNormal(bottomside).cross(getNormal(topside));
            Vec3 planenormal = beforepoint.subtract(farthest).cross(edgevec);
            
            if (ropevec.dot(planenormal) > 0 || ropevec.length() < 0.1) {
                removeSegment(index);
            }
            
            // Remove segments if rope is too short
            while (segments.size() > 1 && getDistToAnchor() > ropeLen) {
                if (segments.size() == 2) break;
                removeSegment(1);
            }
        }
        
        // Update segments for wrapping detection
        if (movingHook && segments.size() > 1) {
            Vec3 farthest = segments.get(1);
            Vec3 prevfarthest = (segments.size() == 2) ? prevPlayerPos : farthest;
            updateSegment(hookPos, prevHookPos, farthest, prevfarthest, 1, 0, level);
        }
        
        if (segments.size() > 1) {
            Vec3 closest = segments.get(segments.size() - 2);
            Vec3 prevclosest = (segments.size() == 2) ? prevHookPos : closest;
            updateSegment(closest, prevclosest, playerPos, prevPlayerPos, segments.size() - 1, 0, level);
        }
        
        prevHookPos = new Vec3(hookPos.x, hookPos.y, hookPos.z);
        prevPlayerPos = new Vec3(playerPos.x, playerPos.y, playerPos.z);
    }
    
    /**
     * Remove a segment from the list (e.g., when player unwraps from corner).
     */
    private void removeSegment(int index) {
        if (index < 0 || index >= segments.size()) return;
        segments.remove(index);
        segmentBottomSides.remove(index);
        segmentTopSides.remove(index);
    }
    
    /**
     * Check if rope between two points intersects a block, and if so, add a wrap segment.
     * 
     * @param top Upper point of rope segment
     * @param prevTop Previous position of upper point
     * @param bottom Lower point of rope segment
     * @param prevBottom Previous position of lower point
     * @param index Current segment index
     * @param recursionDepth Recursion depth (to prevent infinite loops)
     * @param level Game level for raycasting
     */
    private void updateSegment(Vec3 top, Vec3 prevTop, Vec3 bottom, Vec3 prevBottom, 
                               int index, int recursionDepth, Level level) {
        if (recursionDepth > MAX_RECURSIONS) {
            return; // Prevent infinite recursion
        }
        
        // Raycast from bottom to top - check if rope hits a block
        BlockHitResult bottomRayTrace = rayTraceBlocks(level, bottom, top);
        
        if (bottomRayTrace == null) {
            return; // No collision, rope is clear
        }
        
        // Check if collision was already there (no new wrap)
        if (rayTraceBlocks(level, prevBottom, prevTop) != null) {
            return;
        }
        
        Vec3 bottomHitVec = bottomRayTrace.getLocation();
        Direction bottomSide = bottomRayTrace.getDirection();
        Vec3 bottomNormal = getNormal(bottomSide);
        
        // Try to find the corner edge
        Vec3 cornerBound1 = bottomHitVec.add(bottomNormal.scale(-INTO_BLOCK));
        
        // Test three potential corner positions
        Vec3[] cornerBounds = {
            linePlaneIntersection(prevTop, prevBottom, cornerBound1, bottomNormal),
            linePlaneIntersection(top, prevTop, cornerBound1, bottomNormal),
            linePlaneIntersection(prevBottom, bottom, cornerBound1, bottomNormal)
        };
        
        for (Vec3 cornerBound2 : cornerBounds) {
            if (cornerBound2 == null) continue;
            
            // Raycast to find the corner edge
            BlockHitResult cornerRayTrace = rayTraceBlocks(level, cornerBound2, cornerBound1);
            if (cornerRayTrace == null) continue;
            
            Vec3 cornerHitPos = cornerRayTrace.getLocation();
            Direction cornerSide = cornerRayTrace.getDirection();
            
            // Skip if corner is on the same face as bottom
            if (cornerSide == bottomSide || cornerSide.getOpposite() == bottomSide) {
                continue;
            }
            
            // Calculate bend point (where rope wraps around corner)
            Vec3 actualCorner = cornerHitPos.add(bottomNormal.scale(INTO_BLOCK));
            Vec3 bend = actualCorner
                .add(bottomNormal.scale(BEND_OFFSET))
                .add(getNormal(cornerSide).scale(BEND_OFFSET));
            
            Vec3 topRopeVec = bend.subtract(top);
            Vec3 bottomRopeVec = bend.subtract(bottom);
            
            // Skip if bend is too close to adjacent segments
            if (topRopeVec.length() < 0.05) {
                if (index > 0 && 
                    segmentBottomSides.get(index - 1) == bottomSide && 
                    segmentTopSides.get(index - 1) == cornerSide) {
                    continue;
                }
            }
            
            if (bottomRopeVec.length() < 0.05) {
                if (segmentBottomSides.get(index) == bottomSide && 
                    segmentTopSides.get(index) == cornerSide) {
                    continue;
                }
            }
            
            // Add the bend segment
            actuallyAddSegment(index, bend, bottomSide, cornerSide);
            
            // Check if we have enough rope length
            if (getDistToAnchor() + 0.2 > ropeLen) {
                removeSegment(index);
                continue;
            }
            
            // Recursively check the upper portion of the rope
            double newRopeLen = topRopeVec.length() + bottomRopeVec.length();
            double prevTopToBend = topRopeVec.length() * 
                prevTop.subtract(prevBottom).length() / newRopeLen;
            Vec3 prevBend = prevTop.add(prevBottom.subtract(prevTop).scale(prevTopToBend));
            
            updateSegment(top, prevTop, bend, prevBend, index, recursionDepth + 1, level);
            return; // Exit after finding one corner
        }
    }
    
    /**
     * Add a new segment to the wrap list.
     */
    private void actuallyAddSegment(int index, Vec3 bendPoint, Direction bottomSide, Direction topSide) {
        segments.add(index, bendPoint);
        segmentBottomSides.add(index, bottomSide);
        segmentTopSides.add(index, topSide);
    }
    
    /**
     * Calculate line-plane intersection using parametric formula.
     * See: https://en.wikipedia.org/wiki/Line%E2%80%93plane_intersection#Algebraic_form
     */
    private Vec3 linePlaneIntersection(Vec3 linePoint1, Vec3 linePoint2, 
                                       Vec3 planePoint, Vec3 planeNormal) {
        Vec3 lineVec = linePoint2.subtract(linePoint1);
        double denominator = lineVec.dot(planeNormal);
        
        // Check if line is parallel to plane
        if (Math.abs(denominator) < 1e-6) {
            return null;
        }
        
        double t = planePoint.subtract(linePoint1).dot(planeNormal) / denominator;
        return linePoint1.add(lineVec.scale(t));
    }
    
    /**
     * Get unit vector for a block face direction.
     */
    private Vec3 getNormal(Direction facing) {
        if (facing == null) {
            return new Vec3(0, 0, 0);
        }
        var normal = facing.getNormal();
        return new Vec3(normal.getX(), normal.getY(), normal.getZ());
    }
    
    /**
     * Raycast from start to end, return first block hit.
     * Uses Minecraft's built-in clipping (collision detection).
     */
    private BlockHitResult rayTraceBlocks(Level level, Vec3 start, Vec3 end) {
        ClipContext context = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, 
            (net.minecraft.world.entity.Entity) null);
        BlockHitResult result = level.clip(context);
        
        if (result.getType() == HitResult.Type.BLOCK) {
            return result;
        }
        return null;
    }
    
    /**
     * Get total distance from first segment (hook) to anchor point.
     */
    private double getDistToAnchor() {
        if (segments.size() < 2) return 0.0;
        double total = 0.0;
        for (int i = 0; i < segments.size() - 1; i++) {
            total += segments.get(i).distanceTo(segments.get(i + 1));
        }
        return total;
    }
    
    /**
     * Get all segment points (for rendering).
     */
    public Vec3[] getSegments() {
        return segments.toArray(new Vec3[0]);
    }
    
    /**
     * Get total number of wrap points.
     */
    public int getSegmentCount() {
        return segments.size();
    }
    
    /**
     * Check if rope is currently wrapping around anything.
     */
    public boolean isWrapped() {
        return segments.size() > 2;
    }
}
