import test from "node:test";
import assert from "node:assert/strict";
import { projectPlanPoint, projectWorldPoint } from "../src/projection.js";

test("floorplan reverses the horizontal screen order of door and window", () => {
  const door = [5, 3, 0];
  const window = [2, 3, 0];
  const planDoor = projectPlanPoint(door, 10, 10, 1, 0, 0);
  const planWindow = projectPlanPoint(window, 10, 10, 1, 0, 0);
  assert.ok(planDoor[0] < planWindow[0]);
});

test("Y points upward in the floorplan and Y and height point upward in 3D", () => {
  assert.ok(projectPlanPoint([0, 1], 0, 10, 1, 0, 0)[1] <
    projectPlanPoint([0, 0], 0, 10, 1, 0, 0)[1]);
  assert.ok(projectWorldPoint([0, 1, 0], [0, 0, 0], 0, .7)[1] <
    projectWorldPoint([0, 0, 0], [0, 0, 0], 0, .7)[1]);
  assert.ok(projectWorldPoint([0, 0, 1], [0, 0, 0], 0, .7)[1] <
    projectWorldPoint([0, 0, 0], [0, 0, 0], 0, .7)[1]);
});
