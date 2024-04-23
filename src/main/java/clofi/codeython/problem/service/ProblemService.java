package clofi.codeython.problem.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import clofi.codeython.member.domain.Member;
import clofi.codeython.member.repository.MemberRepository;
import clofi.codeython.problem.controller.response.AllProblemResponse;
import clofi.codeython.problem.controller.response.BaseCodeResponse;
import clofi.codeython.problem.controller.response.GetProblemResponse;
import clofi.codeython.problem.controller.response.RecordResponse;
import clofi.codeython.problem.controller.response.TestcaseResponse;
import clofi.codeython.problem.domain.Problem;
import clofi.codeython.problem.domain.Record;
import clofi.codeython.problem.domain.request.BaseCodeRequest;
import clofi.codeython.problem.domain.request.CreateProblemRequest;
import clofi.codeython.problem.domain.request.TestcaseRequest;
import clofi.codeython.problem.repository.LanguageRepository;
import clofi.codeython.problem.repository.ProblemRepository;
import clofi.codeython.problem.repository.RecordRepository;
import clofi.codeython.problem.repository.TestcaseRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ProblemService {
    private final ProblemRepository problemRepository;
    private final LanguageRepository languageRepository;
    private final TestcaseRepository testcaseRepository;
    private final RecordRepository recordRepository;
    private final MemberRepository memberRepository;

    public Long createProblem(CreateProblemRequest createProblemRequest) {
        if (problemRepository.existsByTitle(createProblemRequest.getTitle())) {
            throw new IllegalArgumentException("이미 만들어진 문제 제목입니다.");
        }

        Problem problem = problemRepository.save(createProblemRequest.toProblem());
        for (BaseCodeRequest baseCode : createProblemRequest.getBaseCodes()) {
            languageRepository.save(
                createProblemRequest.toLanguage(problem, baseCode.getLanguage(), baseCode.getCode()));
        }
        for (TestcaseRequest testcase : createProblemRequest.getTestcase()) {
            testcaseRepository.save(
                createProblemRequest.toTestcase(problem, testcase.getInputCase(), testcase.getOutputCase(),
                    testcase.getDescription()));
        }

        return problem.getProblemNo();
    }

    public List<AllProblemResponse> getAllProblem(Member tokenMember) {
        Member member = memberRepository.findByUsername(tokenMember.getUsername());
        List<Problem> problems = problemRepository.findAll();

        return problems.stream().map(problem -> {
            List<Record> records = recordRepository.findAllByProblemAndMember(problem, member);
            Optional<Record> highestAccuracy = records.stream().max(Comparator.comparingInt(Record::getAccuracy));
            return highestAccuracy.map(record -> AllProblemResponse.of(problem, record.getAccuracy(), true))
                .orElseGet(() -> AllProblemResponse.of(problem, 0, false));
        }).collect(Collectors.toList());

    }

    public GetProblemResponse getProblem(Long problemNo, Member tokenMember) {
        Member member = memberRepository.findByUsername(tokenMember.getUsername());
        if (problemRepository.findByProblemNo(problemNo) == null) {
            throw new EntityNotFoundException("등록된 문제가 없습니다.");
        }
        Problem problem = problemRepository.findByProblemNo(problemNo);

        List<Record> records = recordRepository.findAllByProblemAndMemberOrderByCreatedAtDesc(problem, member);
        List<BaseCodeResponse> baseCodes = languageRepository.findByProblem(problem)
            .stream()
            .map(bc -> {
                Optional<Record> record = records.stream()
                    .filter(r -> r.getLanguage().equals(bc.getLanguage().name()))
                    .findAny();
                return record.map(r -> new BaseCodeResponse(bc.getLanguage(), r.getWrittenCode()))
                    .orElseGet(() -> new BaseCodeResponse(bc.getLanguage(), bc.getBaseCode()));
            })
            .collect(Collectors.toList());

        List<TestcaseResponse> testcases = testcaseRepository.findByProblem(problem)
            .stream()
            .map(tc -> new TestcaseResponse(tc.getInput(), tc.getOutput(), tc.getDescription()))
            .collect(Collectors.toList());

        return GetProblemResponse.of(
            problem,
            baseCodes,
            testcases);
    }

    public List<RecordResponse> getRecord(String userName) {
        Member member = memberRepository.findByUsername(userName);
        return recordRepository.findAllByMemberOrderByUpdatedAtDesc(member).stream()
            .map(record -> {
                Problem problem = problemRepository.findByProblemNo(record.getProblem().getProblemNo());
                return RecordResponse.of(record, problem.getTitle());
            }).collect(Collectors.toList());
    }
}
