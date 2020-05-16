from __future__ import absolute_import, division, print_function

import cv2
import numpy as np


def compute_scale_and_shift(prediction, target, mask):
    """From https://gist.github.com/ranftlr/a1c7a24ebb24ce0e2f2ace5bce917022
    """

    # system matrix: A = [[a_00, a_01], [a_10, a_11]]
    a_00 = np.sum(mask * prediction * prediction)
    a_01 = np.sum(mask * prediction)
    a_11 = np.sum(mask)

    # right hand side: b = [b_0, b_1]
    b_0 = np.sum(mask * prediction * target)
    b_1 = np.sum(mask * target)
    x_0 = np.zeros_like(b_0)
    x_1 = np.zeros_like(b_1)

    det = a_00 * a_11 - a_01 * a_01
    # A needs to be a positive definite matrix.
    valid = det > 0

    x_0[valid] = (a_11[valid] * b_0[valid] - a_01[valid] * b_1[valid]) / det[valid]
    x_1[valid] = (-a_01[valid] * b_0[valid] + a_00[valid] * b_1[valid]) / det[valid]

    return x_0, x_1


def read_files(pred, gt):
    pred = cv2.imread(pred, -1) / 256.0
    gt = cv2.imread(gt, -1) / 256.0
    return pred, gt


def evaluate(opts):
    """Evaluates a pretrained model using a specified test set
        similarly to https://gist.github.com/ranftlr/a1c7a24ebb24ce0e2f2ace5bce917022
    """

    print("   Mono evaluation - using least squares method")
    PRED = "prediction.png"
    GT = "gt.png"
    prediction, target = read_files(PRED, GT)
    mask = (target > 0) & (target < opts["max_depth"])
    target_disparity = np.zeros_like(target)
    target_disparity[mask == 1] = 1.0 / target[mask == 1]
    print(mask.sum())
    scale, shift = compute_scale_and_shift(prediction, target_disparity, mask)
    print("scale: {} shift:{}".format(scale, shift))
    prediction_aligned = scale * prediction + shift
    disparity_cap = 1.0 / opts["max_depth"]
    prediction_aligned[prediction_aligned < disparity_cap] = disparity_cap
    prediciton_depth = 1.0 / prediction_aligned
    prediciton_depth = prediciton_depth[mask == 1]
    target = target[mask == 1]


if __name__ == "__main__":
    opts = {"max_depth": 10}
    evaluate(opts)
